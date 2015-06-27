package biz.wiz.jira.plugins.irc;
// {{{ import
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import javax.net.ssl.SSLSocketFactory;

import biz.wiz.jira.plugins.irc.beans.IrcAdminConfig;
import biz.wiz.jira.plugins.irc.beans.IrcProjectConfig;

import org.pircbotx.Configuration;
import org.pircbotx.Colors;
import org.pircbotx.PircBotX;

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
		//ircConnect();
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
		ircDisconnect();
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

		String channelName = getChannelName(settings, projectId);
		// LOGGER.debug(String.format("channelName = %s", channelName));

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
	private void ircConnect() // {{{
	{
		if (isIrcActive(settings) != true)
		{
			return;
		}

		String ircServerName = getIrcServerName(settings);
		//LOGGER.debug("irc server name = " + ircServerName);
		Integer ircServerPort = getIrcServerPort(settings);
		//LOGGER.debug("irc server port = " + ircServerPort);

		if (irc == null)
		{
			Configuration config= new Configuration.Builder()
				.setLogin("jira")
				.setName("jira")
				.setAutoNickChange(true)
				.setServer("irc.tokyo.jp", 7000)
				.addAutoJoinChannel("#test")
				.setSocketFactory(SSLSocketFactory.getDefault())
				.setRealName("JIRA IRC Plugin")
				.buildConfiguration();

			this.irc = new PircBotX(config);
			try
			{
				irc.startBot();
			}
			catch (Exception ex)
			{
				// LOGGER.error(String.format("error connecting to IRC: %s", ex.toString()));
			}
		}

		if (irc.isConnected())
		{
			return;
		}

		List<Project> projects = projectManager.getProjectObjects();
		for (Project project : projects)
		{
			String projectId = project.getId().toString();
			String projectName = project.getName();
			// LOGGER.debug(String.format("projectName = %s, projectId = %s", projectName, projectId));

			if (isIrcActive(settings) && isIrcChannelActive(settings, projectId))
			{
				String channelName = getChannelName(settings, projectId);
				// LOGGER.debug(String.format("channelName = %s", channelName));
				irc.sendIRC().joinChannel(channelName);
			}
		}
	} // }}}
	private void ircDisconnect() // {{{
	{
		if (irc != null && irc.isConnected())
		{
			irc.sendIRC().quitServer("bye");
		}
	} // }}}
	// helper methods
	private void sendMessage(String projectId, String channelName, String message) // {{{
	{
		if (isIrcActive(settings) == false || isIrcChannelActive(settings, projectId) == false)
		{
			return;
		}

		if (irc == null)
		{
			return;
		}

		/*
		if (Arrays.asList(irc.getChannels()).contains(channelName) == false)
		{
			// LOGGER.info(String.format("join channel (%s)", channelName));
			irc.sendIRC().joinChannel(channelName);
		}
		*/

		if (isIrcChannelNotice(settings, projectId))
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
	private String getIrcServerName(PluginSettings settings) // {{{
	{
		return (String) settings.get(IrcAdminConfig.class.getName() + ".ircServerName");
	} // }}}
	private Integer getIrcServerPort(PluginSettings settings) // {{{
	{
		String ircServerPort = (String) settings.get(IrcAdminConfig.class.getName() + ".ircServerPort");
		if (ircServerPort != null)
		{
			return Integer.parseInt(ircServerPort);
		}
		return null;
	} // }}}
	private String getChannelName(PluginSettings settings, String projectId) // {{{
	{
		return (String)settings.get(
			IrcProjectConfig.class.getName() + "_" + projectId + ".channelName"
		);
	} // }}}
	private boolean isIrcChannelActive(PluginSettings settings, String projectId) // {{{
	{
		return Boolean.parseBoolean(
			(String)settings.get(
				IrcProjectConfig.class.getName() + "_" + projectId + ".active"
			)
		);
	} // }}}
	private boolean isIrcChannelNotice(PluginSettings settings, String projectId) // {{{
	{
		return Boolean.parseBoolean(
			(String)settings.get(
				IrcProjectConfig.class.getName() + "_" + projectId + ".notice"
			)
		);
	} // }}}
	private boolean isIrcActive(PluginSettings settings) // {{{
	{
		return Boolean.parseBoolean(
			(String)settings.get(
				IrcAdminConfig.class.getName() + ".active"
			)
		);
	} // }}}
}
// vim: foldmethod=marker wrap
