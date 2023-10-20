package py.com.semp.lib.socket;

import java.net.Socket;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import py.com.semp.lib.socket.configuration.SocketConfiguration;
import py.com.semp.lib.utilidades.communication.DataInterface;
import py.com.semp.lib.utilidades.communication.DataReceiver;
import py.com.semp.lib.utilidades.communication.DataTransmitter;
import py.com.semp.lib.utilidades.communication.listeners.ConnectionEventListener;
import py.com.semp.lib.utilidades.communication.listeners.DataListener;
import py.com.semp.lib.utilidades.configuration.ConfigurationValues;
import py.com.semp.lib.utilidades.exceptions.CommunicationException;
import py.com.semp.lib.utilidades.internal.MessageUtil;
import py.com.semp.lib.utilidades.internal.Messages;

public class SocketDriver implements DataInterface, DataReceiver, DataTransmitter
{
	private Socket socket;
	private SocketConfiguration configurationValues;
	private final CopyOnWriteArraySet<DataListener> dataListeners = new CopyOnWriteArraySet<>();
	private final CopyOnWriteArraySet<ConnectionEventListener> connectionEventListeners = new CopyOnWriteArraySet<>();
	private volatile boolean connected;
	private String stringIdentifier;
	
	public SocketDriver()
	{
		super();
		
		this.setSocket(new Socket());
	}
	
	public SocketDriver(Socket socket)
	{
		super();
		
		this.setSocket(socket);
	}
	
	private void setSocket(Socket socket2)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void sendData(byte[] data) throws CommunicationException
	{
		// TODO Auto-generated method stub
		
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
	
	@Override
	public byte[] readData() throws CommunicationException
	{
		// TODO Auto-generated method stub
		return null;
	}
	
	@Override
	public DataInterface connect() throws CommunicationException
	{
		// TODO Auto-generated method stub
		return null;
	}
	
	@Override
	public DataInterface connect(ConfigurationValues configurationValues) throws CommunicationException
	{
		// TODO Auto-generated method stub
		return null;
	}
	
	@Override
	public DataInterface disconnect() throws CommunicationException
	{
		// TODO Auto-generated method stub
		return null;
	}
	
	@Override
	public DataInterface setConfigurationValues(ConfigurationValues configurationValues) throws CommunicationException
	{
		// TODO Auto-generated method stub
		return null;
	}
	
	@Override
	public ConfigurationValues getConfigurationValues()
	{
		// TODO Auto-generated method stub
		return null;
	}
	
	@Override
	public SocketDriver addConnectionEventListeners(ConnectionEventListener... connectionEventListeners)
	{
		if(connectionEventListeners == null)
		{
			StringBuilder methodName = new StringBuilder();
			
			methodName.append("DataInterface ");
			methodName.append(this.getClass().getSimpleName());
			methodName.append("::");
			methodName.append("addConnectionEventListeners(ConnectionEventListener...)");
			
			String errorMessage = MessageUtil.getMessage(Messages.NULL_VALUES_NOT_ALLOWED_ERROR, methodName.toString());
			
			throw new NullPointerException(errorMessage);
		}
		
		this.connectionEventListeners.addAll(Set.of(connectionEventListeners));
		
		return this;
	}
	
	@Override
	public DataInterface removeConnectionEventListeners(ConnectionEventListener... connectionEventListeners)
	{
		// TODO Auto-generated method stub
		return null;
	}
	
	@Override
	public DataInterface removeAllConnectionEventListeners()
	{
		// TODO Auto-generated method stub
		return null;
	}
	
	@Override
	public String getStringIdentifier()
	{
		// TODO Auto-generated method stub
		return null;
	}
	
	@Override
	public boolean isConnected()
	{
		// TODO Auto-generated method stub
		return false;
	}
}