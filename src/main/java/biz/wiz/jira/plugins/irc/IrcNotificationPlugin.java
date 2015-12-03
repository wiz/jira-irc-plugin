package biz.wiz.jira.plugins.irc;
// {{{ import
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
//import javax.net.ssl.SSLSocketFactory;

import biz.wiz.jira.plugins.irc.beans.IrcAdminConfig;
import biz.wiz.jira.plugins.irc.beans.IrcProjectConfig;

import org.pircbotx.Configuration;
import org.pircbotx.Colors;
import org.pircbotx.PircBotX;
import org.pircbotx.UtilSSLSocketFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.commons.lang.StringUtils;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

import com.atlassian.core.util.DateUtils;
import com.atlassian.crowd.embedded.api.User;
import com.atlassian.event.api.EventListener;
import com.atlassian.event.api.EventPublisher;
import com.atlassian.jira.event.issue.IssueEvent;
import com.atlassian.jira.event.type.EventType;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.comments.Comment;
import com.atlassian.jira.issue.worklog.Worklog;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.project.ProjectManager;
import com.atlassian.jira.util.velocity.VelocityRequestContextFactory;
import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
// }}}
/**
 * Simple JIRA listener using the atlassian-event library and demonstrating
 * plugin lifecycle integration.
 */
public class IrcNotificationPlugin
implements InitializingBean, DisposableBean
{
	// {{{ member variables
	private static final Logger LOGGER = LoggerFactory.getLogger(IrcNotificationPlugin.class);
	private final EventPublisher eventPublisher;
	private final PluginSettingsFactory pluginSettingsFactory;
	private final VelocityRequestContextFactory velocityRequestContextFactory;
	private final ProjectManager projectManager;
	private PluginSettings settings;
	private PircBotX irc;
	// }}}
	/**
	 * Constructor.
	 * @param eventPublisher
	 *            injected {@code EventPublisher} implementation.
	 */
	public IrcNotificationPlugin // {{{
	(
		EventPublisher eventPublisher,
		PluginSettingsFactory pluginSettingsFactory,
		VelocityRequestContextFactory velocityRequestContextFactory,
		ProjectManager projectManager
	)
	{
		this.eventPublisher = eventPublisher;
		this.pluginSettingsFactory = pluginSettingsFactory;
		this.velocityRequestContextFactory = velocityRequestContextFactory;
		this.projectManager = projectManager;
		this.irc = null;
	} // }}}
	/**
	 * Called when the plugin has been enabled.
	 * @throws Exception
	 */
	@Override
	public void afterPropertiesSet()
	throws Exception
	{ // {{{
		this.settings = pluginSettingsFactory.createGlobalSettings();
		ircCreate();
		// register ourselves with the EventPublisher
		eventPublisher.register(this);
	} // }}}
	/**
	 * Called when the plugin is being disabled or removed.
	 * @throws Exception
	 */
	@Override
	public void destroy()
	throws Exception
	{ // {{{
		ircDestroy();
		// unregister ourselves with the EventPublisher
		eventPublisher.unregister(this);
	} // }}}
	/**
	 * Receives any {@code IssueEvent}s sent by JIRA.
	 * @param issueEvent
	 *            the IssueEvent passed to us
	 */
	@EventListener
	public synchronized void onIssueEvent(IssueEvent issueEvent)
	{
		// {{{ variables
		Project project = issueEvent.getIssue().getProjectObject();
		String projectId = project.getId().toString();
		Long eventTypeId = issueEvent.getEventTypeId();
		Issue issue = issueEvent.getIssue();
		String issueTypeName = issue.getIssueTypeObject().getNameTranslation();
		String issueKey = issue.getKey();
		String issueSummary = issue.getSummary();
		String userDisplayName = issueEvent.getUser().getDisplayName();
		String userName = issueEvent.getUser().getName();

		String channelName = getIrcChannelForProject(settings, projectId);
		LOGGER.debug(String.format("channelName = %s", channelName));

		// TODO: add local variables for colors and set using config option

		// }}}
		if (eventTypeId.equals(EventType.ISSUE_ASSIGNED_ID)) // {{{
		{
			// line 1
			String message = String.format(
					Colors.RED + "[%s] " + Colors.NORMAL +
					"%s Assigned: " +
					Colors.BOLD + "%s" + Colors.NORMAL +
					" <- %s",
				issueKey, issueTypeName, issueSummary, getIssueUrl(issue));
				//issueKey, userDisplayName, userName, assigneeUserDisplayName, assigneeUserName, issueTypeName, issueSummary);
			sendNotification(projectId, channelName, message);
			// line 2
			String assigneeUserDisplayName = issue.getAssigneeUser().getDisplayName();
			String assigneeUserName = issue.getAssigneeUser().getName();
			message = message.concat(String.format("%s (%s) assigned to %s (%s)", assigneeUserDisplayName, assigneeUserName));
			// line 3
			sendIssueEventComment(settings, projectId, channelName, issueEvent);
		} // }}}
		else if (eventTypeId.equals(EventType.ISSUE_CLOSED_ID)) // {{{
		{
			// line 1
			String message = String.format(
					Colors.RED + "[%s] " + Colors.NORMAL +
					"%s Closed: " +
					Colors.BOLD + "%s" + Colors.NORMAL +
					" <- %s",
				issueKey, issueTypeName, issueSummary, getIssueUrl(issue));
			sendNotification(projectId, channelName, message);
			// line 2
			message = String.format("%s (%s) closed %s", userDisplayName, userName, issueKey);
			sendNotification(projectId, channelName, message);
			// line 3
			sendIssueEventComment(settings, projectId, channelName, issueEvent);
		} // }}}
		else if (eventTypeId.equals(EventType.ISSUE_COMMENTED_ID) || eventTypeId.equals(EventType.ISSUE_COMMENT_EDITED_ID)) // {{{
		{
			Comment comment = issueEvent.getComment();
			User authorUser = comment.getAuthorUser();
			String authUserDisplayName = authorUser.getDisplayName();
			String authUserName = authorUser.getName();
			String commentBody = StringUtils.abbreviate(comment.getBody(), 20);

			// line 1
			String message = String.format(
					Colors.RED + "[%s] " + Colors.NORMAL +
					"%s Comment: " +
					Colors.BOLD + "%s" + Colors.NORMAL +
					" <- %s" +
					"?focusedCommentId=%d" +
					"&page=com.atlassian.jira.plugin.system.issuetabpanels:comment-tabpanel" +
					"#comment-%d",
				issueKey, issueTypeName, issueSummary, getIssueUrl(issue),
				comment.getId().longValue(), comment.getId().longValue());
			sendNotification(projectId, channelName, message);
			// line 2
			message = String.format("%s (%s) commented on %s", userDisplayName, userName, issueKey);
			sendNotification(projectId, channelName, message);
			// line 3
			sendIssueEventComment(settings, projectId, channelName, issueEvent);
		} // }}}
		else if (eventTypeId.equals(EventType.ISSUE_CREATED_ID)) // {{{
		{
			boolean hasAssigneeUser = issue.getAssigneeUser() != null;

			// line 1
			String message = String.format(
					Colors.RED + "[%s] " + Colors.NORMAL +
					"%s Created: " +
					Colors.BOLD + "%s" + Colors.NORMAL +
					" <- %s",
				issueKey, issueTypeName, issueSummary, getIssueUrl(issue));
			sendNotification(projectId, channelName, message);
			// line 2
			if (hasAssigneeUser)
			{
				String assigneeUserDisplayName = issue.getAssigneeUser().getDisplayName();
				String assigneeUserName = issue.getAssigneeUser().getName();
				message = String.format(
						"%s (%s) created and assigned %s to %s (%s)",
					userDisplayName, userName, issueKey, assigneeUserDisplayName, assigneeUserName);
			}
			else
			{
				message = String.format("%s (%s) created %s", userDisplayName, userName, issueKey);
			}
			sendNotification(projectId, channelName, message);
			// line 3
			sendIssueEventComment(settings, projectId, channelName, issueEvent);
		} // }}}
		else if (eventTypeId.equals(EventType.ISSUE_DELETED_ID)) // {{{
		{
			boolean hasAssigneeUser = issue.getAssigneeUser() != null;

			// line 1
			String message = String.format(
					Colors.RED + "[%s] " + Colors.NORMAL +
					"%s Deleted: " +
					Colors.BOLD + "%s" + Colors.NORMAL +
					" <- %s",
				issueKey, issueTypeName, issueSummary, getIssueUrl(issue));
			sendNotification(projectId, channelName, message);
			// line 2
			message = String.format("%s (%s) deleted %s", userDisplayName, userName, issueKey);
			if (hasAssigneeUser)
			{
				String assigneeUserDisplayName = issue.getAssigneeUser().getDisplayName();
				String assigneeUserName = issue.getAssigneeUser().getName();
				message = message.concat(String.format(" - assigned to %s (%s)", assigneeUserDisplayName, assigneeUserName));
			}
			sendNotification(projectId, channelName, message);
		} // }}}
		else if (eventTypeId.equals(EventType.ISSUE_MOVED_ID)) // {{{
		{
			// TODO
		} // }}}
		else if (eventTypeId.equals(EventType.ISSUE_REOPENED_ID)) // {{{
		{
			boolean hasAssigneeUser = issue.getAssigneeUser() != null;

			// line 1
			String message = String.format(
					Colors.RED + "[%s] " + Colors.NORMAL +
					"%s Re-Opened: " +
					Colors.BOLD + "%s" + Colors.NORMAL +
					" <- %s",
				issueKey, issueTypeName, issueSummary, getIssueUrl(issue));
			sendNotification(projectId, channelName, message);
			// line 2
			message = String.format("%s (%s) re-opened %s", userDisplayName, userName, issueKey);
			if (hasAssigneeUser)
			{
				String assigneeUserDisplayName = issue.getAssigneeUser().getDisplayName();
				String assigneeUserName = issue.getAssigneeUser().getName();
				message = message.concat(String.format(" - assigned to %s (%s)", assigneeUserDisplayName, assigneeUserName));
			}
			sendNotification(projectId, channelName, message);
			// line 3
			sendIssueEventComment(settings, projectId, channelName, issueEvent);
		} // }}}
		else if (eventTypeId.equals(EventType.ISSUE_RESOLVED_ID)) // {{{
		{
			// line 1
			String message = String.format(
					Colors.RED + "[%s] " + Colors.NORMAL +
					"%s Resolved: " +
					Colors.BOLD + "%s" + Colors.NORMAL +
					" <- %s",
				issueKey, issueTypeName, issueSummary, getIssueUrl(issue));
			sendNotification(projectId, channelName, message);
			// line 2
			message = String.format("%s (%s) resolved %s", userDisplayName, userName, issueKey);
			sendNotification(projectId, channelName, message);
			// line 3
			sendIssueEventComment(settings, projectId, channelName, issueEvent);
		} // }}}
		else if (eventTypeId.equals(EventType.ISSUE_UPDATED_ID)) // {{{
		{
			// line 1
			String message = String.format(
					Colors.RED + "[%s] " + Colors.NORMAL +
					"%s Updated: " +
					Colors.BOLD + "%s" + Colors.NORMAL +
					" <- %s",
				issueKey, issueTypeName, issueSummary, getIssueUrl(issue));
			sendNotification(projectId, channelName, message);
			// line 2
			message = String.format("%s (%s) updated %s", userDisplayName, userName, issueKey);
			sendNotification(projectId, channelName, message);
			// line 3
			sendIssueEventComment(settings, projectId, channelName, issueEvent);
		} // }}}
		else if (eventTypeId.equals(EventType.ISSUE_WORKLOGGED_ID)) // {{{
		{
			Worklog worklog = issueEvent.getWorklog();
			String authorFullName = worklog.getAuthorFullName();
			String author = worklog.getAuthor();

			String message = String.format(
					Colors.RED + "[%s] " + Colors.NORMAL +
					"%s (%s) logged work on %s: " +
					Colors.BOLD + "%s" + Colors.NORMAL,
				issueKey, authorFullName, author, issueTypeName, issueSummary);

			sendNotification(projectId, channelName, message);
			Long timeSpent = worklog.getTimeSpent();
			sendTimeSpent(projectId, channelName, timeSpent);

			if (StringUtils.isNotBlank(worklog.getComment()))
			{
				String comment = StringUtils.abbreviate(worklog.getComment(), 20);
				sendNotification(projectId, channelName, String.format("\"%s\"", comment));
			}

			sendIssueUrl( settings, channelName, issue,
					String.format(
							"?focusedWorklogId=%s&page=com.atlassian.jira.plugin.system.issuetabpanels&worklog-tabpanel#worklog-%s",
					worklog.getId().toString(), worklog.getId().toString()));
		} // }}}
	/*
		else if (eventTypeId.equals(EventType.ISSUE_WORKLOG_DELETED_ID)) // {{{
		{
			// TODO
		} // }}}
		else if (eventTypeId.equals(EventType.ISSUE_WORKLOG_UPDATED_ID)) // {{{
		{
			// TODO
		} // }}}
	*/
		else if (eventTypeId.equals(EventType.ISSUE_WORKSTARTED_ID)) // {{{
		{
			String message = String.format(
					Colors.RED + "[%s] " + Colors.NORMAL +
					"%s (%s) has started working on %s: " +
					Colors.BOLD + "%s" + Colors.NORMAL,
				issueKey, userDisplayName, userName, issueTypeName, issueSummary);

			sendNotification(projectId, channelName, message);
		} // }}}
		else if (eventTypeId.equals(EventType.ISSUE_WORKSTOPPED_ID)) // {{{
		{
			String message = String.format(
					Colors.RED + "[%s] " + Colors.NORMAL +
					"%s (%s) stopped working on %s: " +
					Colors.BOLD + "%%s" + Colors.NORMAL,
				issueKey, userDisplayName, userName, issueTypeName, issueSummary);

			sendNotification(projectId, channelName, message);
		} // }}}
		else // {{{
		{
			LOGGER.info(String.format("unhandled eventTypeId %s", eventTypeId));
		} // }}} 
	}
	// IRC methods
	private void ircCreate() // {{{
	{
		LOGGER.info("ircCreate()");
		// irc is already initialized, destroy existing instance first
		if (irc != null)
		{
			ircDestroy();
		}

		// only run if irc is enabled
		if (isIrcActive(settings) != true)
		{
			return;
		}

		// build config for irc client
		Configuration.Builder configBuilder = new Configuration.Builder();

		// get server hostname and port
		String ircServerHost = getIrcServerHost(settings);
		String ircServerPassword = getIrcServerPassword(settings);
		Integer ircServerPort = getIrcServerPort(settings);

		LOGGER.debug("irc server name = " + ircServerHost);
		LOGGER.debug("irc server port = " + ircServerPort);

		if (ircServerPassword != null)
		{
			LOGGER.debug("irc server has password");
			configBuilder.setServer(ircServerHost, ircServerPort, ircServerPassword);
		}
		else
		{
			configBuilder.setServer(ircServerHost, ircServerPort);
		}
		configBuilder.setAutoReconnect(true);

		// set to use ssl
		if (isIrcServerSSL(settings))
		{
			configBuilder.setSocketFactory(new UtilSSLSocketFactory().trustAllCertificates());
		}

		// set irc client user and nick
		String ircServerNick = getIrcServerNick(settings);
		configBuilder.setLogin(ircServerNick);
		configBuilder.setName(ircServerNick);
		configBuilder.setAutoNickChange(true);

		configBuilder.setRealName("JIRA IRC Plugin");

		// iterate thru all projects, add channels to irc client config
		List<Project> projects = projectManager.getProjectObjects();
		for (Project project : projects)
		{
			String projectId = project.getId().toString();
			String projectName = project.getName();
			LOGGER.debug(String.format("projectName = %s, projectId = %s", projectName, projectId));

			if (isIrcActive(settings) && isIrcActiveForProject(settings, projectId) && isIrcJoinChannelForProject(settings, projectId))
			{
				String channelName = getIrcChannelForProject(settings, projectId);
				//irc.sendIRC().joinChannel(channelName);
				LOGGER.debug(String.format("channelName = %s", channelName));
				configBuilder.addAutoJoinChannel(channelName);
			}
		}

		// create the irc client using our config
		LOGGER.debug("creating PircBotX and launching in its own thread)");
		this.irc = new PircBotX(configBuilder.buildConfiguration());
		// start the irc client in a new thread
		new Thread(new Runnable()
		{
			@Override
			public void run()
			{
				try
				{
					irc.startBot();
					LOGGER.debug("starting PircBotX");
				}
				catch (Exception ex)
				{
					LOGGER.error(String.format("error connecting to IRC: %s", ex.toString()));
				}
			}
		}).start();

	} // }}}
	private void ircDestroy() // {{{
	{
		LOGGER.info("ircDestroy()");
		if (irc != null)
		{
			try
			{
				irc.stopBotReconnect();
				irc.sendIRC().quitServer("bye");
			}
			catch (Exception ex)
			{
				LOGGER.error(String.format("error sending QUIT to IRC: %s", ex.toString()));
				// TODO forcefully interrupt thread?
			}
			this.irc = null;
		}
	} // }}}
	// helper methods
	private void sendNotification(String projectId, String channelName, String message) // {{{
	{
		LOGGER.info(String.format("sendNotification to %s -> %s", channelName, message));
		if (irc == null)
		{
			ircCreate();
		}

		if (isIrcActive(settings) == false || isIrcActiveForProject(settings, projectId) == false)
		{
			return;
		}

		/* // what if two projects contradict each other on the join setting for same channel??
		if (isIrcJoinChannelForProject() == false) // TOOD: if user changed setting to no longer be external message
		{
			LOGGER.info(String.format("join channel (%s)", channelName));
			irc.sendIRC().joinChannel(channelName);
		}
		*/

		// TODO: if not connected, get new config and force restart
		if (isIrcNoticeForProject(settings, projectId))
		{
			irc.sendIRC().notice(channelName, message);
		}
		else
		{
			irc.sendIRC().message(channelName, message);
		}
	} // }}}
	private void sendIssueEventComment(PluginSettings settings, String projectId, String channelName, IssueEvent issueEvent) // {{{
	{
		if (issueEvent.getComment() != null && StringUtils.isNotBlank(issueEvent.getComment().getBody()))
		{
			String comment = StringUtils.abbreviate(issueEvent.getComment().getBody(), 60);
			sendNotification(projectId, channelName, String.format("\"%s\"", comment));
		}
	} // }}}
	private void sendTimeSpent(String projectId, String channelName, Long timeSpent) // {{{
	{
		if (timeSpent != null)
		{
			sendNotification(projectId, channelName, String.format("Time Worked: " + DateUtils.getDurationString(timeSpent, 8, 5)));
		}
	} // }}}
	private void sendIssueUrl(PluginSettings settings, String channelName, Issue issue, String option) // {{{
	{
		String projectId = issue.getProjectObject().getId().toString();
		String url = getIssueUrl(issue);
		sendNotification(projectId, channelName, url.concat(option));
	} // }}}
	private String getIssueUrl(Issue issue) // {{{
	{
		String url = String.format("%s/browse/%s",
				velocityRequestContextFactory.getJiraVelocityRequestContext()
						.getCanonicalBaseUrl(), issue.getKey());
		return url;
	} // }}}
	// config methods
	private String getIrcServerHost(PluginSettings settings) // {{{
	{
		String ircServerHost = (String)settings.get(
			IrcAdminConfig.class.getName() + ".ircServerHost"
		);
		if (ircServerHost == null || ircServerHost.equals(""))
		{
			return "127.0.0.1";
		}
		return ircServerHost;
	} // }}}
	private String getIrcServerPassword(PluginSettings settings) // {{{
	{
		String ircServerPassword = (String)settings.get(
			IrcAdminConfig.class.getName() + ".ircServerPassword"
		);
		if (ircServerPassword.equals(""))
		{
			return null;
		}
		return ircServerPassword;
	} // }}}
	private Integer getIrcServerPort(PluginSettings settings) // {{{
	{
		String ircServerPort = (String) settings.get(IrcAdminConfig.class.getName() + ".ircServerPort");
		if (ircServerPort == null || ircServerPort.equals(""))
		{
			return Integer.parseInt("6667");
		}
		return Integer.parseInt(ircServerPort);
	} // }}}
	private String getIrcServerNick(PluginSettings settings) // {{{
	{
		String ircServerNick = (String)settings.get(
			IrcAdminConfig.class.getName() + ".ircServerNick"
		);
		if (ircServerNick == null || ircServerNick.equals(""))
		{
			return "jira";
		}
		return ircServerNick;
	} // }}}
	private String getIrcChannelForProject(PluginSettings settings, String projectId) // {{{
	{
		String channelName = (String)settings.get(
			IrcProjectConfig.class.getName() + "_" + projectId + ".channelName"
		);
		if (channelName == null || channelName.equals(""))
		{
			return "#test";
		}
		return channelName;
	} // }}}
	private boolean isIrcJoinChannelForProject(PluginSettings settings, String projectId) // {{{
	{
		boolean joinChannel = Boolean.parseBoolean(
			(String)settings.get(
				IrcProjectConfig.class.getName() + "_" + projectId + ".joinChannel"
			)
		);
		return joinChannel;
	} // }}}
	private boolean isIrcActiveForProject(PluginSettings settings, String projectId) // {{{
	{
		boolean active = Boolean.parseBoolean(
			(String)settings.get(
				IrcProjectConfig.class.getName() + "_" + projectId + ".active"
			)
		);
		return active;
	} // }}}
	private boolean isIrcNoticeForProject(PluginSettings settings, String projectId) // {{{
	{
		boolean notice = Boolean.parseBoolean(
			(String)settings.get(
				IrcProjectConfig.class.getName() + "_" + projectId + ".notice"
			)
		);
		return notice;
	} // }}}
	private boolean isIrcActive(PluginSettings settings) // {{{
	{
		boolean active = Boolean.parseBoolean(
			(String)settings.get(
				IrcAdminConfig.class.getName() + ".active"
			)
		);
		return active;
	} // }}}
	private boolean isIrcServerSSL(PluginSettings settings) // {{{
	{
		boolean ircServerSSL = Boolean.parseBoolean(
			(String)settings.get(
				IrcAdminConfig.class.getName() + ".ircServerSSL"
			)
		);
		return ircServerSSL;
	} // }}}
}
// vim: foldmethod=marker wrap
