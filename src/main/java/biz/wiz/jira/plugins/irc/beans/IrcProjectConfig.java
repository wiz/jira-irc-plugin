package biz.wiz.jira.plugins.irc.beans;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
public final class IrcProjectConfig
{
	@XmlElement
	private Boolean active;

	@XmlElement
	private String channelName;

	@XmlElement
	private Boolean joinChannel;

	@XmlElement
	private Boolean notice;

	@XmlElement
	private Boolean noColors;

	public String getChannelName()
	{
		return channelName;
	}

	public void setChannelName(String channelName)
	{
		this.channelName = channelName;
	}

	public Boolean getJoinChannel()
	{
		return joinChannel;
	}

	public void setJoinChannel(Boolean joinChannel)
	{
		this.joinChannel = joinChannel;
	}

	public Boolean getNoColors()
	{
		return noColors;
	}

	public void setNoColors(Boolean noColors)
	{
		this.noColors = noColors;
	}

	public Boolean getNotice()
	{
		return notice;
	}

	public void setNotice(Boolean notice)
	{
		this.notice = notice;
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
