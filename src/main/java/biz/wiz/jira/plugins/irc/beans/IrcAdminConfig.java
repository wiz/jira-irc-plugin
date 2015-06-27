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
	private Boolean ircServerSSL;

	@XmlElement
	private String ircClientNick;

	@XmlElement
	private Boolean active;

	public String getIrcServerHost()
	{
		return ircServerHost;
	}

	public void setIrcServerHost(String ircServerHost)
	{
		this.ircServerHost = ircServerHost;
	}

	public int getIrcServerPort()
	{
		return ircServerPort;
	}

	public void setIrcServerPort(int ircServerPort)
	{
		this.ircServerPort = ircServerPort;
	}

	public Boolean getIrcServerSSL()
	{
		return ircServerSSL;
	}

	public void setIrcServerSSL(Boolean ircServerSSL)
	{
		this.ircServerSSL = ircServerSSL;
	}

	public String getIrcClientNick()
	{
		return ircClientNick;
	}

	public void setIrcClientNick(String ircClientNick)
	{
		this.ircClientNick = ircClientNick;
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
