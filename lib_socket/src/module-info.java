/**
 * @author Sergio Morel
 */
module lib_socket
{
	exports py.com.semp.lib.socket;
	exports py.com.semp.lib.socket.configuration;
	exports py.com.semp.lib.socket.readers;
	exports py.com.semp.lib.socket.drivers;
	
	requires transitive lib_utilidades;
	requires org.junit.jupiter.api;
}