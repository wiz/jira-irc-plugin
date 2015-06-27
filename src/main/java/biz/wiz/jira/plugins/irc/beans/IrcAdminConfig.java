package biz.wiz.jira.plugins.irc.beans;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
public final class IrcAdminConfig
{
	@XmlElement
	private String ircServerHost;

	@XmlElement
	private int ircServerPort;

	@XmlElement
	private Boolean useSSL;

	@XmlElement
	private String nick;

	@XmlElement
	private Boolean active;

	public String getServerHost()
	{
		return ircServerHost;
	}

	public void setServerHost(String ircServerHost)
	{
		this.ircServerHost = ircServerHost;
	}

	public int getServerPort()
	{
		return ircServerPort;
	}

	public void setServerPort(int ircServerPort)
	{
		this.ircServerPort = ircServerPort;
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
