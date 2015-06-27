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

// import org.slf4j.Logger;
// import org.slf4j.LoggerFactory;

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
	//private static final Logger LOGGER = LoggerFactory.getLogger(AdminServlet.class);
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
		// LOGGER.debug(String.format("channelName = %s", channelName));

		// TODO: add local variables for colors and set using config option

		// }}}
		if (eventTypeId == EventType.ISSUE_CREATED_ID) // {{{
		{
			boolean hasAssigneeUser = issue.getAssigneeUser() != null;

			String message = String.format(
					Colors.RED + "[%s] " + Colors.NORMAL +
					"%s (%s) has created %s: " +
					Colors.BOLD + "%s" + Colors.NORMAL,
				issueKey, userDisplayName, userName, issueTypeName, issueSummary);

			if (hasAssigneeUser)
			{
				String assigneeUserDisplayName = issue.getAssigneeUser().getDisplayName();
				String assigneeUserName = issue.getAssigneeUser().getName();
				message = message.concat(String.format(" - assigned to %s (%s)", assigneeUserDisplayName, assigneeUserName));
			}
			sendMessage(projectId, channelName, message);
			sendIssueEventComment(settings, projectId, channelName, issueEvent);
			sendIssueUrl(channelName, issue);
		} // }}}
		else if (eventTypeId == EventType.ISSUE_ASSIGNED_ID) // {{{
		{
			String assigneeUserDisplayName = issue.getAssigneeUser().getDisplayName();
			String assigneeUserName = issue.getAssigneeUser().getName();

			String message = String.format(
					Colors.RED + "[%s] " + Colors.NORMAL + // issueKey
					" %s (%s) assigned to %s (%s) %s: " +
					Colors.BOLD + "%s" + Colors.NORMAL,
				issueKey, userDisplayName, userName, assigneeUserDisplayName, assigneeUserName, issueTypeName, issueSummary);

			sendMessage(projectId, channelName, message);
			sendIssueUrl(channelName, issue);
		} // }}}
		else if (eventTypeId == EventType.ISSUE_WORKSTARTED_ID) // {{{
		{
			String message = String.format(
					Colors.RED + "[%s] " + Colors.NORMAL +
					"%s (%s) has started working on %s: " +
					Colors.BOLD + "%s" + Colors.NORMAL,
				issueKey, userDisplayName, userName, issueTypeName, issueSummary);

			sendMessage(projectId, channelName, message);
			sendIssueUrl(channelName, issue);
		} // }}}
		else if (eventTypeId == EventType.ISSUE_WORKLOGGED_ID) // {{{
		{
			Worklog worklog = issueEvent.getWorklog();
			String authorFullName = worklog.getAuthorFullName();
			String author = worklog.getAuthor();

			String message = String.format(
					Colors.RED + "[%s] " + Colors.NORMAL +
					"%s (%s) logged work on %s: " +
					Colors.BOLD + "%s" + Colors.NORMAL,
				issueKey, authorFullName, author, issueTypeName, issueSummary);

			sendMessage(projectId, channelName, message);
			Long timeSpent = worklog.getTimeSpent();
			sendTimeSpent(projectId, channelName, timeSpent);

			if (StringUtils.isNotBlank(worklog.getComment()))
			{
				String comment = StringUtils.abbreviate(worklog.getComment(), 20);
				sendMessage(projectId, channelName, String.format("\"%s\"", comment));
			}

			sendIssueUrl( settings, channelName, issue,
					String.format(
							"?focusedWorklogId=%s&page=com.atlassian.jira.plugin.system.issuetabpanels&worklog-tabpanel#worklog-%s",
					worklog.getId().toString(), worklog.getId().toString()));
		} // }}}
		else if (eventTypeId == EventType.ISSUE_WORKSTOPPED_ID) // {{{
		{
			String message = String.format(
					Colors.RED + "[%s] " + Colors.NORMAL +
					"%s (%s) stopped working on %s: " +
					Colors.BOLD + "%%s" + Colors.NORMAL,
				issueKey, userDisplayName, userName, issueTypeName, issueSummary);

			sendMessage(projectId, channelName, message);
			sendIssueUrl(channelName, issue);
		} // }}}
		else if (eventTypeId == EventType.ISSUE_COMMENTED_ID) // {{{
		{
			Comment comment = issueEvent.getComment();
			User authorUser = comment.getAuthorUser();
			String authUserDisplayName = authorUser.getDisplayName();
			String authUserName = authorUser.getName();
			String commentBody = StringUtils.abbreviate(comment.getBody(), 20);

			String message = String.format(
					Colors.RED + "[%s] " + Colors.NORMAL +
					"%s (%s) has commented on %s:  " +
					Colors.BOLD + "%s" + Colors.NORMAL,
				issueKey, authUserDisplayName, authUserName, issueTypeName, issueSummary);

			sendMessage(projectId, channelName, message);
			sendMessage(projectId, channelName, String.format("\"%s\"", commentBody));
			sendIssueUrl(
					settings,
					channelName,
					issue,
					String.format(
							"?focusedCommentId=%d&page=com.atlassian.jira.plugin.system.issuetabpanels:comment-tabpanel#comment-%d",
							comment.getId().longValue(), comment.getId()
									.longValue()));
		} // }}}
		else if (eventTypeId == EventType.ISSUE_RESOLVED_ID) // {{{
		{
			String message = String.format(
					Colors.RED + "[%s] " + Colors.NORMAL +
					"%s (%s) has resolved %s: " +
					Colors.BOLD + "%s" + Colors.NORMAL,
				issueKey, userDisplayName, userName, issueTypeName, issueSummary);

			sendMessage(projectId, channelName, message);
			sendTimeSpent(projectId, channelName, issue.getTimeSpent());
			sendIssueEventComment(settings, projectId, channelName, issueEvent);
			sendIssueUrl(channelName, issue);
		} // }}}
		else if (eventTypeId == EventType.ISSUE_CLOSED_ID) // {{{
		{
			String message = String.format(
					Colors.RED + "[%s] " + Colors.NORMAL +
					"%s (%s) has closed %s: " +
					Colors.BOLD + "%s" + Colors.NORMAL,
				issueKey, userDisplayName, userName, issueTypeName, issueSummary);

			sendMessage(projectId, channelName, message);
			sendIssueUrl(channelName, issue);
		} // }}}
		else if (eventTypeId == EventType.ISSUE_REOPENED_ID) // {{{
		{
			String message = String.format(
					Colors.RED + "[%s] " + Colors.NORMAL +
					"%s (%s) has re-opened %s: " +
					Colors.BOLD + "%s" + Colors.NORMAL,
				issueKey, userDisplayName, userName, issueTypeName, issueSummary);

			sendMessage(projectId, channelName, message);
			sendIssueUrl(channelName, issue);
		} // }}}
	}
	// IRC methods
	private void ircCreate() // {{{
	{
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
		Integer ircServerPort = getIrcServerPort(settings);
		configBuilder.setServer(ircServerHost, ircServerPort);
		//LOGGER.debug("irc server name = " + ircServerHost);
		//LOGGER.debug("irc server port = " + ircServerPort);

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
			// LOGGER.debug(String.format("projectName = %s, projectId = %s", projectName, projectId));

			if (isIrcActive(settings) && isIrcActiveForProject(settings, projectId))
			{
				String channelName = getIrcChannelForProject(settings, projectId);
				//irc.sendIRC().joinChannel(channelName);
				// LOGGER.debug(String.format("channelName = %s", channelName));
				configBuilder.addAutoJoinChannel(channelName);
			}
		}

		// create the irc client using our config
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
				}
				catch (Exception ex)
				{
					// LOGGER.error(String.format("error connecting to IRC: %s", ex.toString()));
				}
			}
		}).start();

	} // }}}
	private void ircDestroy() // {{{
	{
		if (irc != null)
		{
			irc.sendIRC().quitServer("bye");
			this.irc = null;
		}
	} // }}}
	// helper methods
	private void sendMessage(String projectId, String channelName, String message) // {{{
	{
		if (irc == null || isIrcActive(settings) == false || isIrcActiveForProject(settings, projectId) == false)
		{
			return;
		}

		/* // dont need because pircbotx will auto join
		if (Arrays.asList(irc.getIrcChannelForProjects()).contains(channelName) == false)
		{
			// LOGGER.info(String.format("join channel (%s)", channelName));
			irc.sendIRC().joinChannel(channelName);
		}
		*/

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
			sendMessage(projectId, channelName, String.format("\"%s\"", comment));
		}
	} // }}}
	private void sendTimeSpent(String projectId, String channelName, Long timeSpent) // {{{
	{
		if (timeSpent != null)
		{
			sendMessage(projectId, channelName, String.format("Time Worked: " + DateUtils.getDurationString(timeSpent, 8, 5)));
		}
	} // }}}
	private void sendIssueUrl(String channelName, Issue issue) // {{{
	{
		String projectId = issue.getProjectObject().getId().toString();
		String url = getIssueUrl(issue);
		sendMessage(projectId, channelName, url);
	} // }}}
	private void sendIssueUrl(PluginSettings settings, String channelName, Issue issue, String option) // {{{
	{
		String projectId = issue.getProjectObject().getId().toString();
		String url = getIssueUrl(issue);
		sendMessage(projectId, channelName, url.concat(option));
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
