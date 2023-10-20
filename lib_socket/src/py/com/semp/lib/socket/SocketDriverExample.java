package py.com.semp.lib.socket;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import py.com.semp.lib.utilidades.communication.DataInterface;
import py.com.semp.lib.utilidades.communication.DataReceiver;
import py.com.semp.lib.utilidades.communication.DataTransmitter;
import py.com.semp.lib.utilidades.communication.listeners.ConnectionEventListener;
import py.com.semp.lib.utilidades.communication.listeners.DataListener;
import py.com.semp.lib.utilidades.configuration.ConfigurationValues;
import py.com.semp.lib.utilidades.exceptions.CommunicationException;

/**
 * Clase de ejemplo
 */
public class SocketDriverExample implements DataInterface, DataReceiver, DataTransmitter
{
	
	private Socket socket;
	private ConfigurationValues configurationValues;
	private final Set<ConnectionEventListener> connectionStateListeners = new HashSet<>();
	
	@Override
	public DataInterface connect() throws CommunicationException
	{
//		int x = Values.Utilities.Constants.BUFFER_BOUNDARY;
//		try
//		{
			if(configurationValues == null)
			{
				throw new CommunicationException("Configuration not set.");
			}
//			socket = new Socket(configurationValues.getParameter("host"), configurationValues.getParameter("port"));
			for(ConnectionEventListener listener : connectionStateListeners)
			{
				listener.onConnect(Instant.now(), this);
			}
			return this;
//		}
//		catch(IOException e)
//		{
//			throw new CommunicationException("Failed to connect to socket.", e);
//		}
	}
	
	@Override
	public DataInterface connect(ConfigurationValues configurationValues) throws CommunicationException
	{
		this.configurationValues = configurationValues;
		return connect();
	}
	
	@Override
	public DataInterface disconnect() throws CommunicationException
	{
		try
		{
			socket.close();
			for(ConnectionEventListener listener : connectionStateListeners)
			{
				listener.onDisconnect(Instant.now(), this);
			}
			return this;
		}
		catch(IOException e)
		{
			throw new CommunicationException("Failed to close socket.", e);
		}
	}
	
	@Override
	public DataInterface setConfigurationValues(ConfigurationValues configurationValues) throws CommunicationException
	{
		this.configurationValues = configurationValues;
		return this;
	}
	
	@Override
	public ConfigurationValues getConfigurationValues()
	{
		return configurationValues;
	}
	
	@Override
	public DataInterface addConnectionEventListeners(ConnectionEventListener... connectionStateListeners)
	{
		for(ConnectionEventListener listener : connectionStateListeners)
		{
			this.connectionStateListeners.add(listener);
		}
		return this;
	}
	
	@Override
	public DataInterface removeConnectionEventListeners(ConnectionEventListener... connectionStateListeners)
	{
		for(ConnectionEventListener listener : connectionStateListeners)
		{
			this.connectionStateListeners.remove(listener);
		}
		return this;
	}
	
	@Override
	public DataInterface removeAllConnectionEventListeners()
	{
		this.connectionStateListeners.clear();
		return this;
	}
	
	@Override
	public String getStringIdentifier()
	{
		return socket.getInetAddress().toString();
	}
	
	@Override
	public boolean isConnected()
	{
		return socket != null && socket.isConnected() && !socket.isClosed();
	}
	
	@Override
	public byte[] readData() throws CommunicationException
	{
		try
		{
			InputStream inputStream = socket.getInputStream();
			byte[] data = new byte[inputStream.available()];
			inputStream.read(data);
			return data;
		}
		catch(IOException e)
		{
			throw new CommunicationException("Failed to read data from socket.", e);
		}
	}
	
	@Override
	public void sendData(byte[] data) throws CommunicationException
	{
		try
		{
			OutputStream outputStream = socket.getOutputStream();
			outputStream.write(data);
			outputStream.flush();
		}
		catch(IOException e)
		{
			throw new CommunicationException("Failed to send data through socket.", e);
		}
	}
	
	@Override
	public void addDataListeners(DataListener... listeners)
	{
		// TODO Auto-generated method stub
		
	}
	
	@Override
	public void removeDataListeners(DataListener... listeners)
	{
		// TODO Auto-generated method stub
		
	}
	
	@Override
	public void removeAllDataListeners()
	{
		// TODO Auto-generated method stub
		
	}
}
