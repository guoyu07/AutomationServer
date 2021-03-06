package fr.guiet.automationserver.business;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Properties;

import org.apache.log4j.Logger;

import com.pi4j.io.serial.Baud;
import com.pi4j.io.serial.DataBits;
import com.pi4j.io.serial.FlowControl;
import com.pi4j.io.serial.Parity;
import com.pi4j.io.serial.Serial;
import com.pi4j.io.serial.SerialConfig;
import com.pi4j.io.serial.SerialDataEvent;
import com.pi4j.io.serial.SerialDataEventListener;
import com.pi4j.io.serial.SerialFactory;
import com.pi4j.io.serial.StopBits;

import fr.guiet.automationserver.dataaccess.DbManager;
import fr.guiet.automationserver.dto.SMSDto;
import fr.guiet.automationserver.dto.TeleInfoTrameDto;

public class RainGaugeService implements Runnable {

	private static Logger _logger = Logger.getLogger(RainGaugeService.class);
	private String _defaultDevice = "";
	private Serial _serial = null;
	private boolean _isStopped = false; // Service arrete?
	private SMSGammuService _smsGammuService = null;
	private DbManager _dbManager = null;

	public RainGaugeService(SMSGammuService smsGammuService) {
		_smsGammuService = smsGammuService;
		_dbManager = new DbManager();
	}

	@Override
	public void run() {

		_logger.info("Starting RainGaugeService...");

		InputStream is = null;
		try {
			String configPath = System.getProperty("automationserver.config.path");
			is = new FileInputStream(configPath);

			Properties prop = new Properties();
			prop.load(is);

			_defaultDevice = prop.getProperty("raingauge.usbdevice");

		} catch (FileNotFoundException e) {
			_logger.error(
					"Impossible de trouver le fichier de configuration classpath_folder/config/automationserver.properties",
					e);
		} catch (IOException e) {
			_logger.error(
					"Impossible de trouver le fichier de configuration classpath_folder/config/automationserver.properties",
					e);
		}

		_logger.info("Using serial device : " + _defaultDevice);

		CreateSerialInstance();

		while (!_isStopped) {

			try {
				
				// Necessary otherwire, serial reader stop
				Thread.sleep(2000);

			} catch (Exception e) {
				_logger.error("Error occured in RainGauge service...", e);

				SMSDto sms = new SMSDto();
				sms.setMessage("Error occured in RainGauge services service, review error log for more details");
				_smsGammuService.sendMessage(sms, true);
			}
		}

	}

	// Arret du service TeleInfoService
	public void StopService() {

		try {
			if (_serial.isOpen()) {
				_logger.info("Fermeture port serie");
				_serial.close();
			}
		} catch (IOException ioe) {
			_logger.error("Impossible de fermer le port serie", ioe);
		}

		SerialFactory.shutdown();

		_logger.info("Stopping RainGauge service...");

		_isStopped = true;
	}

	private void CreateSerialInstance()  {

		SerialDataEventListener sdl = null;

		if(_serial!=null)
		{
			if (_serial.isOpen())
				try {
					_logger.info("Fermeture du port série...");
					_serial.close();
				} catch (Exception e) {
					_logger.error("Impossible de fermer correctement le port série...", e);
				}
		}

		_serial=SerialFactory.createInstance();

		// open the default serial port provided on the GPIO header at 1200
		// bauds
		// serial.open(_defaultDevice, _defaultBaud);
		SerialConfig config = new SerialConfig();config.device(_defaultDevice).baud(Baud._1200).dataBits(DataBits._7).parity(Parity.EVEN).stopBits(StopBits._1).flowControl(FlowControl.NONE);

		try
		{
			_serial.setBufferingDataReceived(false);

			_serial.open(config);
			_logger.info("Ouverture du port serie effectué avec succès...");

			sdl = CreateSerialListener();

			_serial.addListener(sdl);
			
		} catch(
				IOException e)
		{
			_logger.error("Impossible d'ouvrir le port série");
		}		
	}

	// Creation du listener sur le port serie
	private SerialDataEventListener CreateSerialListener() {

		return new SerialDataEventListener() {
			
			@Override
			public void dataReceived(SerialDataEvent event) {
				_dbManager.SaveRainGaugeBucketTip();				
			}
		};
	}
}