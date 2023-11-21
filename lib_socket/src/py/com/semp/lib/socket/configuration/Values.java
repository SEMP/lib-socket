package py.com.semp.lib.socket.configuration;

/**
 * Contains the value of constants used in the project.
 * 
 * @author Sergio Morel
 */
public interface Values
{
	/**
	 * Contains constant values
	 * 
	 * @author Sergio Morel
	 */
	public interface Constants
	{
		//Integer values
		/**
		 * The size of the thread pool used to inform the socket listeners of the events.
		 */
		public static final int SOCKET_LISTENERS_THREAD_POOL_SIZE = 10;
		
		//String values
		/**
		 * Context name for the socket library
		 */
		public static final String SOCKET_CONTEXT = "lib_socket";
		
		/**
		 * Represents the "any" IP address, which typically indicates that a server 
		 * should listen on all available IP addresses on the host. This IP address
		 * is not intended to be used for routing.
		 */
		public static final String INET_ADDRESS_ANY = "0.0.0.0";
		
		/**
		 * Path where the messages for localization are found.
		 */
		public static final String MESSAGES_PATH = "/py/com/semp/lib/socket/";
	}
	
	/**
	 * Contains variable names
	 * 
	 * @author Sergio Morel
	 */
	public interface VariableNames
	{
		// Integer Variables Names
		/**
		 * The port of the remote host.
		 */
		public static final String REMOTE_PORT = "remotePort";
		
		/**
		 * The port of the local host.
		 */
		public static final String LOCAL_PORT = "localPort";
		
		/**
		 * Time out time in milliseconds for establishing connections.
		 */
		public static final String CONNECTION_TIMEOUT_MS = "connectionTimeoutMS";
		
		/**
		 * The time the socket will block during a read operation before throwing an exception.<br>
		 */
		public static final String READ_TIMEOUT_MS = Values.Utilities.VariableNames.READ_TIMEOUT_MS;
		
		/**
		 * The time the socket will block during a write operation before throwing an exception.<br>
		 */
		public static final String WRITE_TIMEOUT_MS = "writeTimeoutMS";
		
		/**
		 * The time the socket will block during a read operation before throwing an exception.<br>
		 */
		public static final String SOCKET_BUFFER_SIZE_BYTES = "socketBufferSizeBytes";
		
		/**
		 * Time to wait for tasks termination.
		 */
		public static final String TERMINATION_TIMOUT_MS = "terminationTimeoutMS";
		
		/**
		 * Time delay for polls.
		 */
		public static final String POLL_DELAY_MS = "pollDelayMS";
		
		// String Variables Names
		/**
		 * The address of the remote host.
		 */
		public static final String REMOTE_ADDRESS = "remoteAddress";
		
		/**
		 * The address of the local host.
		 */
		public static final String LOCAL_ADDRESS = "localAddress";
		
		/**
		 * The network interface used.
		 */
		public static final String NETWORK_INTERFACE = "networkInterface";
	}
	
	/**
	 * Contains constants with default values.
	 * 
	 * @author Sergio Morel
	 */
	public interface Defaults
	{
		//Integer values
		public static final Integer CONNECTION_TIMEOUT_MS = -1;
		public static final Integer READ_TIMEOUT_MS = -1;
		public static final Integer WRITE_TIMEOUT_MS = -1;
		public static final Integer SOCKET_BUFFER_SIZE_BYTES = 1024 * 2;
		public static final Integer TERMINATION_TIMOUT_MS = Utilities.Constants.TERMINATION_TIMOUT_MS;
		public static final Integer POLL_DELAY_MS = Utilities.Defaults.POLL_DELAY_MS;
	}
	
	/**
	 * Contains resources names
	 * 
	 * @author Sergio Morel
	 */
	public interface Resources
	{
		/**
		 * Base name of the boundle of properties files for each language.
		 */
		public static final String MESSAGES_BASE_NAME = "messages";
	}
	
	/**
	 * Contains the value of constants from the Utilities library.
	 * 
	 * @author Sergio Morel
	 */
	public static interface Utilities extends py.com.semp.lib.utilidades.configuration.Values {}
}