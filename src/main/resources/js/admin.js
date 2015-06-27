AJS.toInit(function()
{
	var baseUrl = AJS.$('meta[name="application-base-url"]').attr('content');

	function populateForm()
	{
		AJS.$.ajax(
		{
			url: baseUrl + '/rest/irc/1.0/globalConfig',
			dataType: 'json',
			success: function(config)
			{
				if (config.active)
				{
					AJS.$('#active').attr('checked', 'checked');
				}
				else
				{
					AJS.$('#active').removeAttr('checked');
				}

				AJS.$('#ircServerHost').attr('value', config.ircServerHost);
				AJS.$('#ircServerPort').attr('value', config.ircServerPort);
				if (config.useSSL)
				{
					AJS.$('#useSSL').attr('checked', 'checked');
				}
				else
				{
					AJS.$('#useSSL').removeAttr('checked');
				}
			}
		});
	}

	function updateConfig()
	{
		data =
		{
			active: (AJS.$('#active').attr('checked') == 'checked'),
			ircServerHost: AJS.$('#ircServerHost').attr('value'),
			ircServerPort: AJS.$('#ircServerPort').attr('value'),
			useSSL: (AJS.$('#useSSL').attr('checked') == 'checked')
		}
		AJS.$.ajax(
		{
			url: baseUrl + '/rest/irc/1.0/globalConfig',
			type: 'PUT',
			contentType: 'application/json',
			data: JSON.stringify(data)
		});
	}

	populateForm();
	pageModified = false;

	AJS.$('#admin').submit(function(e)
	{
		e.preventDefault();
		updateConfig();
	});
});
