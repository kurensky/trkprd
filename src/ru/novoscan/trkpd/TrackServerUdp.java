package ru.novoscan.trkpd;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.sql.SQLException;
import java.text.ParseException;

import org.apache.log4j.Logger;

import ru.novoscan.trkpd.resources.ModConstats;
import ru.novoscan.trkpd.terminals.ModTeltonikaFm;
import ru.novoscan.trkpd.terminals.ModTranskomT15;
import ru.novoscan.trkpd.terminals.ModWialon;
import ru.novoscan.trkpd.utils.ModConfig;
import ru.novoscan.trkpd.utils.TrackPgUtils;

public class TrackServerUdp implements TrackServer {

	private static final Logger logger = Logger.getLogger(TrackServerUdp.class);

	private ModConfig config;

	private TrackPgUtils pgConnect;
	
	private DatagramSocket listenSocket;

	public TrackServerUdp(ModConfig config, TrackPgUtils pgConnect) {
		this.pgConnect = pgConnect;
		this.config = config;
	}
	@Override
	public void setConfig(ModConfig config) {
		this.config = config;
	}
	@Override
	public ModConfig getConfig() {
		return this.config;
	}
	@Override
	public void setPgConnect(TrackPgUtils pgConnect) {
		this.pgConnect = pgConnect;
	}
	@Override
	public void run() throws IOException {
		logger.debug("Запуск UDP сервера : " + config.getHost() + ":"
				+ config.getPort());
		listenSocket = new DatagramSocket(config.getPort(),
				config.getHost());
		logger.debug("Сервер запущен.");
		while (true) {
			new UDPReader();
		}
	}

	class UDPReader extends Thread implements ModConstats {
		private DatagramPacket dataPacket;
		public UDPReader() {
			try {
				dataPacket = new DatagramPacket(new byte[65507], 65507);
				listenSocket.receive(dataPacket);
				this.start();
			} catch (IOException e) {
				logger.error("Ошибка ввода/вывода : " + e.getMessage());
			}
		}

		public void run() {
			try {
				logger.info("Подключение клиента : "
						+ dataPacket.getSocketAddress().toString());
				pgConnect.connect();
				int modType = config.getModType();
				double readBytes = 0;
				logger.debug("Тип модуля : " + config.getModType());
				if (modType == TERM_TYPE_TRANSKOM_T15) {
					ModTranskomT15 mod = new ModTranskomT15(dataPacket,
							listenSocket, config, pgConnect);
					readBytes = mod.getReadBytes();
				} else if (modType == TERM_TYPE_TELTONIKA_FM) {
					ModTeltonikaFm mod = new ModTeltonikaFm(dataPacket,
							listenSocket, config, pgConnect);
					readBytes = mod.getReadBytes();
				} else if (modType == TERM_TYPE_WIALON) {
					ModWialon mod = new ModWialon(dataPacket,
							listenSocket, config, pgConnect);
					readBytes = mod.getReadBytes();
				} else {
					throw new ParseException("Неподдерживаемый тип модуля",
							modType);
				}
				logger.info("Получено байт : " + readBytes);
				pgConnect.close();
			} catch (ParseException e) {
				logger.error("Неподдерживаемый тип модуля : "
						+ e.getErrorOffset());
			} catch (SQLException e) {
				logger.error("Ошибка БД : " + e.getMessage());
				e.printStackTrace();
			} catch (IOException e) {
				logger.error("Соединение закрыто : " + e.getMessage());
				e.printStackTrace();
			} catch (Exception e) {
				logger.error("Ошибка : " + e.getMessage());
				e.printStackTrace();
			} finally {
				try {
					pgConnect.close();
					logger.info("Соединение с базой закрыто");
				} catch (SQLException e) {
					logger.error("Ошибка закрытия соединения с БД : "
							+ e.getLocalizedMessage());
				}
			}

		}
	}

}
