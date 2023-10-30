package py.com.semp.lib.socket.configuration;

import py.com.semp.lib.socket.SocketChannelDriver;
import py.com.semp.lib.utilidades.exceptions.CommunicationException;

public class Borrar
{
	public static void main(String[] args) throws CommunicationException
	{
		SocketChannelDriver socket = new SocketChannelDriver();
		
		socket.connect("127.0.0.1", 8789);
		
		socket.sendData("hola!!!\n");
		
		socket.disconnect();
	}
}
