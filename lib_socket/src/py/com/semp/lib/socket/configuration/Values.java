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
		public static final String MESSAGES_PATH = "py/com/semp/lib/socket";
	}
	
	/**
	 * Contains variable names
	 * 
	 * @author Sergio Morel
	 */
	public interface VariableNames
	{
		// Integer Variable Names
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
		public static final String READ_TIMEOUT_MS = "readTimeoutMS";
		
		// String Variable Names
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
		public static final Integer CONNECTION_TIMEOUT_MS = 5000;
		public static final Integer READ_TIMEOUT_MS = 1000;
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
	
	/**
	 * Contains the value of constants from the Logs library.
	 * 
	 * @author Sergio Morel
	 */
	public static interface Logs extends py.com.semp.lib.log.configuration.Values {}
}