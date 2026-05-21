package py.com.semp.lib.socket.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.URL;
import java.util.Locale;
import java.util.ResourceBundle;

import org.junit.jupiter.api.Test;

import py.com.semp.lib.socket.configuration.Values;

class MessageUtilTest
{
	@Test
	void testResource()
	{
		String name = Values.Constants.MESSAGES_PATH + Values.Resources.MESSAGES_BASE_NAME + ".properties";
		
		URL resource = this.getClass().getResource(name);
		
		assertNotNull(resource, name);
	}
	
	@Test
	void testResourceBundle()
	{
		ResourceBundle bundle = ResourceBundle.getBundle
		(
			"py/com/semp/lib/socket/messages",
			Locale.getDefault()
		);
		
		String message = bundle.getString("REQUIRED_CONFIGURATION_VALUE_NOT_FOUND_ERROR");
		
		assertNotNull(message);
	}
	
	@Test
	void testResourceBundle2()
	{
		ResourceBundle bundle = ResourceBundle.getBundle
		(
			"/py/com/semp/lib/socket/messages",
			Locale.getDefault(),
			Thread.currentThread().getContextClassLoader()
		);
		
		String message = bundle.getString("REQUIRED_CONFIGURATION_VALUE_NOT_FOUND_ERROR");
		
		assertNotNull(message);
	}
	
	@Test
	void testGetMessage()
	{
		String message = MessageUtil.getMessage(Messages.REQUIRED_CONFIGURATION_VALUE_NOT_FOUND_ERROR);
		
		assertNotNull(message, message);
	}
	
	@Test
	void testGetEnglishMessage()
	{
		Locale originalLocale = Locale.getDefault();
		
		MessageUtil.setLocale(Locale.ENGLISH);
		
		String message = MessageUtil.getMessage(Messages.REQUIRED_CONFIGURATION_VALUE_NOT_FOUND_ERROR);
		
		assertEquals("Required configuration value not found.", message);
		
		MessageUtil.setLocale(originalLocale);
	}
	
	@Test
	void testGetSpanishMessage()
	{
		Locale originalLocale = Locale.getDefault();
		
		MessageUtil.setLocale(new Locale("es"));
		
		String message = MessageUtil.getMessage(Messages.REQUIRED_CONFIGURATION_VALUE_NOT_FOUND_ERROR);
		
		assertEquals("Valor de configuración requerido no ha sido encontrado.", message);
		
		MessageUtil.setLocale(originalLocale);
	}
}