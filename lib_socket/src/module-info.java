/**
 * @author Sergio Morel
 */
module lib_socket
{
	exports py.com.semp.lib.socket;
	exports py.com.semp.lib.socket.configuration;
	
	requires transitive lib_utilidades;
	requires org.junit.jupiter.api;
}