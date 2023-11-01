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
	NULL_VALUES_NOT_ALLOWED_ERROR("NULL_VALUES_NOT_ALLOWED_ERROR"),
	VALUE_SHOULD_NOT_BE_NULL_ERROR("VALUE_SHOULD_NOT_BE_NULL_ERROR"),
	UNABLE_TO_SET_CONFIGURATION_ERROR("UNABLE_TO_SET_CONFIGURATION_ERROR"),
	WRONG_CONFIGURATION_OBJECT_ERROR("WRONG_CONFIGURATION_OBJECT_ERROR"),
	REQUIRED_CONFIGURATION_VALUE_NOT_FOUND_ERROR("REQUIRED_CONFIGURATION_VALUE_NOT_FOUND_ERROR"),
	SOCKET_CLOSED_OR_NOT_CONNECTED_ERROR("SOCKET_CLOSED_OR_NOT_CONNECTED_ERROR"),
	FAILED_TO_SEND_DATA_ERROR("FAILED_TO_SEND_DATA_ERROR"),
	LISTENER_THROWN_EXCEPTION_ERROR("LISTENER_THROWN_EXCEPTION_ERROR"),
	DISCONNECTING_ERROR("DISCONNECTING_ERROR"),
	SHUTDOWN_ERROR("SHUTDOWN_ERROR"),
	CREATE_OBJECT_ERROR("CREATE_OBJECT_ERROR"),
	TASK_SHUTDOWN_ERROR("TASK_SHUTDOWN_ERROR"),
	UNABLE_TO_OBTAIN_VALUE_ERROR("UNABLE_TO_OBTAIN_VALUE_ERROR"),
	ALREADY_CONNECTED_ERROR("ALREADY_CONNECTED_ERROR"),
	CONNECTING_ERROR("CONNECTING_ERROR"),
	CONNECTING_TIMOUT_ERROR("CONNECTING_TIMOUT_ERROR"),
	FAILED_TO_RECEIVE_DATA_ERROR("FAILED_TO_RECEIVE_DATA_ERROR"),
	END_OF_STREAM_REACHED("END_OF_STREAM_REACHED"),
	READING_TIMOUT_ERROR("READING_TIMOUT_ERROR"),
	WRITTING_TIMOUT_ERROR("WRITTING_TIMOUT_ERROR");
	
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