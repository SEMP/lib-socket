package py.com.semp.lib.socket.configuration;

import static py.com.semp.lib.socket.configuration.Values.VariableNames.*;
import py.com.semp.lib.utilidades.configuration.ConfigurationValues;

public class SocketConfiguration extends ConfigurationValues
{
	public SocketConfiguration()
	{
		super();
	}
	
	@Override
	protected void setRequiredParameters()
	{
		this.addRequiredParameter(String.class, REMOTE_ADDRESS);
		this.addRequiredParameter(Integer.class, REMOTE_PORT);
		this.addRequiredParameter(Integer.class, CONNECTION_TIMEOUT_MS);
		this.addRequiredParameter(Integer.class, READ_TIMEOUT_MS);
	}
	
	@Override
	protected void setOptionalParameters()
	{
		this.addOptionalParameter(String.class, NETWORK_INTERFACE);
		this.addOptionalParameter(String.class, LOCAL_ADDRESS);
		this.addOptionalParameter(Integer.class, LOCAL_PORT);
	}
	
	@Override
	protected void setDefaultValues()
	{
		this.setParameter(Integer.class, CONNECTION_TIMEOUT_MS, Values.Defaults.CONNECTION_TIMEOUT_MS);
		this.setParameter(Integer.class, READ_TIMEOUT_MS, Values.Defaults.READ_TIMEOUT_MS);
	}
}