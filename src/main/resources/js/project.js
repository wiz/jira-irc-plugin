AJS.toInit(function() {
	var baseUrl = AJS.$('meta[name="application-base-url"]').attr('content');
	var projectId = AJS.$('#projectId').attr('value');
	function populateForm()
	{
		AJS.$.ajax(
		{
			url: baseUrl + '/rest/irc/1.0/channelConfig/' + projectId,
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

				if (config.notice)
				{
					AJS.$('#notice').attr('checked', 'checked');
				}
				else
				{
					AJS.$('#notice').removeAttr('checked');
				}

				AJS.$('#channelName').attr('value', config.channelName);
			}
		});
	}

	function updateConfig()
	{
		data =
		{
			active: (AJS.$('#active').attr('checked') == 'checked'),
			notice: (AJS.$('#notice').attr('checked') == 'checked'),
			channelName: AJS.$('#channelName').attr('value'),
			messageWithoutJoin: (AJS.$('#messageWithoutJoin').attr('value') == 'checked'),
			noColors: (AJS.$('#noColors').attr('value') == 'checked')
		};
		AJS.$.ajax(
		{
			url: baseUrl + '/rest/irc/1.0/channelConfig/' + projectId,
			type: 'PUT',
			contentType: 'application/json',
			data: JSON.stringify(data)
		});
	}

	populateForm();
	pageModified = false;

	AJS.$('#project').submit(function(e)
	{
		e.preventDefault();
		updateConfig();
	});
});
