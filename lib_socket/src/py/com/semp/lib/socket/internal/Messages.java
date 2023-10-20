package py.com.semp.lib.socket.internal;

import py.com.semp.lib.utilidades.messages.MessageKey;

/**
 * Messages for the utilities library.
 * 
 * @author Sergio Morel
 */
public enum Messages implements MessageKey
{
	DONT_INSTANTIATE("DONT_INSTANTIATE"),
	INDEX_OUT_OF_BOUNDS("INDEX_OUT_OF_BOUNDS"),
	NULL_VALUES_NOT_ALLOWED_ERROR("NULL_VALUES_NOT_ALLOWED_ERROR"),
	VALUE_SHOULD_NOT_BE_NULL_ERROR("VALUE_SHOULD_NOT_BE_NULL_ERROR"),
	UNABLE_TO_SET_CONFIGURATION_ERROR("UNABLE_TO_SET_CONFIGURATION_ERROR");
	
	private final String messageKey;
	
	private Messages(String messageKey)
	{
		this.messageKey = messageKey;
	}
	
	@Override
	public String getMessageKey()
	{
		return messageKey;
	}
}