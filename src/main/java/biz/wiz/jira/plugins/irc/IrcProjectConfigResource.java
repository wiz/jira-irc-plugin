package biz.wiz.jira.plugins.irc;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import biz.wiz.jira.plugins.irc.beans.IrcProjectConfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.atlassian.sal.api.transaction.TransactionCallback;
import com.atlassian.sal.api.transaction.TransactionTemplate;
import com.atlassian.sal.api.user.UserManager;

@Path("/channelConfig")
public class IrcProjectConfigResource
{
	private final UserManager userManager;
	private final PluginSettingsFactory pluginSettingsFactory;
	private final TransactionTemplate transactionTemplate;
	private static final Logger LOGGER = LoggerFactory.getLogger(IrcProjectConfigResource.class);

	public IrcProjectConfigResource
	(
		UserManager userManager,
		PluginSettingsFactory pluginSettingsFactory,
		TransactionTemplate transactionTemplate
	)
	{
		this.userManager = userManager;
		this.pluginSettingsFactory = pluginSettingsFactory;
		this.transactionTemplate = transactionTemplate;
	}

	@GET
	@Path("{projectId}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response get
	(
		@PathParam("projectId") final String projectId,
		@Context HttpServletRequest request
	)
	{
		LOGGER.debug(String.format("get : start(%s,%s)",projectId,request));
		String username = userManager.getRemoteUsername(request);
		Response response = Response.ok
		(
			transactionTemplate.execute(
				new TransactionCallback()
				{
					public Object doInTransaction()
					{
						PluginSettings settings = pluginSettingsFactory.createGlobalSettings();
						IrcProjectConfig config = new IrcProjectConfig();

						// active or not
						String active = (String) settings.get(IrcProjectConfig.class.getName() + "_" + projectId + ".active");
						config.setActive((active != null && Boolean.parseBoolean(active)));

						// channel for project
						String channelName = (String) settings.get(IrcProjectConfig.class.getName() + "_" + projectId + ".channelName");
						if (channelName != null)
						{
							config.setChannelName(channelName);
						}

						// use privmsg or notice
						String notice = (String) settings.get(IrcProjectConfig.class.getName() + "_" + projectId + ".notice");
						config.setNotice((notice != null && Boolean.parseBoolean(notice)));

						// add colors to notification
						String noColors = (String) settings.get(IrcProjectConfig.class.getName() + "_" + projectId + ".noColors");
						config.setNoColors((noColors != null && Boolean.parseBoolean(noColors)));

						// notify without joining
						String joinChannel = (String) settings.get(IrcProjectConfig.class.getName() + "_" + projectId + ".joinChannel");
						config.setJoinChannel((joinChannel != null && Boolean.parseBoolean(joinChannel)));

						return config;
					}
				}
			)
		).build();
		LOGGER.debug(String.format("get : finished(%s)",response));
		return response;
	}

	@PUT
	@Path("{projectId}")
	@Consumes(MediaType.APPLICATION_JSON)
	public Response put
	(
		@PathParam("projectId") final String projectId,
		final IrcProjectConfig config,
		@Context HttpServletRequest request
	)
	{
		String username = userManager.getRemoteUsername(request);
		transactionTemplate.execute(new TransactionCallback()
		{
			public Object doInTransaction()
			{
				PluginSettings pluginSettings = pluginSettingsFactory.createGlobalSettings();
				pluginSettings.put(IrcProjectConfig.class.getName() + "_" + projectId + ".active", config.getActive().toString());
				pluginSettings.put(IrcProjectConfig.class.getName() + "_" + projectId + ".channelName", config.getChannelName());
				pluginSettings.put(IrcProjectConfig.class.getName() + "_" + projectId + ".notice", config.getNotice().toString());
				pluginSettings.put(IrcProjectConfig.class.getName() + "_" + projectId + ".joinChannel", config.getJoinChannel().toString());
				pluginSettings.put(IrcProjectConfig.class.getName() + "_" + projectId + ".noColors", config.getNoColors().toString());
				return null;
			}
		});

		return Response.noContent().build();
	}
}
