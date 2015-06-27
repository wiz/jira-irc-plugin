package biz.wiz.jira.plugins.irc;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import biz.wiz.jira.plugins.irc.beans.IrcAdminConfig;

// import org.slf4j.Logger;
// import org.slf4j.LoggerFactory;

import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.atlassian.sal.api.transaction.TransactionCallback;
import com.atlassian.sal.api.transaction.TransactionTemplate;
import com.atlassian.sal.api.user.UserManager;

@Path("/globalConfig")
public class IrcAdminConfigResource
{
	private final UserManager userManager;
	private final PluginSettingsFactory pluginSettingsFactory;
	private final TransactionTemplate transactionTemplate;
	//private static final Logger LOGGER = LoggerFactory .getLogger(IrcAdminConfigResource.class);

	public IrcAdminConfigResource
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
	@Produces(MediaType.APPLICATION_JSON)
	public Response get(@Context HttpServletRequest request)
	{
		// LOGGER.debug(String.format("get : start(%s)", request));
		String username = userManager.getRemoteUsername(request);
		if (username != null && !userManager.isSystemAdmin(username))
		{
			// LOGGER.debug(String.format("get : finished(%s)", request));
			return Response.status(Status.UNAUTHORIZED).build();
		}
		Response result = Response.ok(
			transactionTemplate.execute(new TransactionCallback()
			{
				@Override
				public Object doInTransaction()
				{
					PluginSettings settings = pluginSettingsFactory.createGlobalSettings();
					IrcAdminConfig config = new IrcAdminConfig();

					String active = (String)settings.get(IrcAdminConfig.class.getName() + ".active");
					config.setActive((active != null && Boolean.parseBoolean(active)));
					config.setServerHost((String)settings.get(IrcAdminConfig.class.getName() + ".ircServerHost"));
					String ircServerPort = (String)settings.get(IrcAdminConfig.class.getName() + ".ircServerPort");
					if (ircServerPort != null)
					{
						config.setServerPort(Integer.parseInt(ircServerPort));
					}
					String useSSL = (String) settings.get(IrcAdminConfig.class.getName() + ".useSSL");
					config.setUseSSL((useSSL != null && Boolean.parseBoolean(useSSL)));
					config.setNick((String) settings.get(IrcAdminConfig.class.getName() + ".nick"));

					return config;
				}
			})
		).build();
		// LOGGER.debug(String.format("get : finished(%s)", result));
		return result;
	}

	@PUT
	@Consumes(MediaType.APPLICATION_JSON)
	public Response put(final IrcAdminConfig config, @Context HttpServletRequest request)
	{
		String username = userManager.getRemoteUsername(request);
		if (username != null && !userManager.isSystemAdmin(username))
		{
			return Response.status(Status.UNAUTHORIZED).build();
		}

		transactionTemplate.execute(new TransactionCallback()
		{
			@Override
			public Object doInTransaction()
			{
				PluginSettings pluginSettings = pluginSettingsFactory .createGlobalSettings();
				pluginSettings.put(IrcAdminConfig.class.getName() + ".active", config.getActive().toString());
				pluginSettings.put(IrcAdminConfig.class.getName() + ".ircServerHost", config.getServerHost());
				pluginSettings.put(IrcAdminConfig.class.getName() + ".ircServerPort", Integer.toString(config.getServerPort()));
				return null;
			}
		});

		return Response.noContent().build();
	}
}
