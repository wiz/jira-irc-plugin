package biz.wiz.jira.plugins.irc.beans;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
public final class ServerConfig
{
	@XmlElement
	private String serverHostname;

	@XmlElement
	private int serverPort;

	@XmlElement
	private Boolean useSSL;

	@XmlElement
	private String nick;

	@XmlElement
	private Boolean active;

	public String getChannelName()
	{
		return serverHostname;
	}

	public void setChannelName(String serverHostname)
	{
		this.serverHostname = serverHostname;
	}

	public int getServerPort()
	{
		return serverPort;
	}

	public void setServerPort(int serverPort)
	{
		this.serverPort = serverPort;
	}

	public Boolean getUseSSL()
	{
		return useSSL;
	}

	public void setUseSSL(Boolean useSSL)
	{
		this.useSSL = useSSL;
	}

	public String getNick()
	{
		return nick;
	}

	public void setNick(String nick)
	{
		this.nick = nick;
	}

	public Boolean getActive()
	{
		return active;
	}

	public void setActive(Boolean active)
	{
		this.active = active;
	}
}
