package de.jeisfeld.mymeditation.http;

import android.content.Context;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;

import de.jeisfeld.mymeditation.Application;


/**
 * Helper class for sending http(s) messages to server.
 */
public class HttpSender {
	/**
	 * The context.
	 */
	private final Context context;

	/**
	 * Constructor.
	 *
	 * @param context The context.
	 */
	public HttpSender(final Context context) {
		this.context = context;
	}

	/**
	 * Send a POST message to Server.
	 *
	 * @param urlPostfix    The postfix of the URL.
	 * @param listener      The response listener.
	 * @param userMessage   The user message.
	 * @param systemMessage The system message.
	 * @param parameters    The additional POST parameters.
	 */
	public void sendMessage(final String urlPostfix, final OnHttpResponseListener listener,
							final String userMessage, final String systemMessage, final String... parameters) {
		Authenticator.setDefault(new Authenticator() {
			@Override
			protected PasswordAuthentication getPasswordAuthentication() {
				return new PasswordAuthentication(HttpCredentials.USERNAME, HttpCredentials.PASSWORD.toCharArray());
			}
		});
		String urlBase = "https://coachat.de/";

		new Thread(() -> {
			Reader in = null;
			try {
				URL url = new URL(urlBase + urlPostfix);
				URLConnection urlConnection = url.openConnection();
				urlConnection.setDoOutput(true);
				((HttpsURLConnection) urlConnection).setRequestMethod("POST");
				urlConnection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
				byte[] postDataBytes = getPostData(userMessage, systemMessage, parameters).getBytes(StandardCharsets.UTF_8);
				urlConnection.setRequestProperty("Content-Length", String.valueOf(postDataBytes.length));
				urlConnection.getOutputStream().write(postDataBytes);
				in = new BufferedReader(new InputStreamReader(urlConnection.getInputStream(), StandardCharsets.UTF_8));
				StringBuilder result = new StringBuilder();
				for (int c; (c = in.read()) >= 0; ) {
					result.append((char) c);
				}
				if (listener != null) {
					ResponseData responseData = ResponseData.extractResponseData(context, result.toString());
					listener.onHttpResponse(result.toString(), responseData);
				}
			}
			catch (IOException e) {
				Log.e(Application.TAG, "Invalid URL", e);
			}
			finally {
				if (in != null) {
					try {
						in.close();
					}
					catch (IOException e) {
						// ignore
					}
				}
			}
		}).start();
	}


	/**
	 * Get post data from the parameters, which are name value entries.
	 *
	 * @param userMessage   The user message.
	 * @param systemMessage The system message.
	 * @param parameters    the name value entries.
	 * @return The data to be posted.
	 */
	private String getPostData(final String userMessage, final String systemMessage, final String... parameters) throws UnsupportedEncodingException {
		StringBuilder postData = new StringBuilder();
		postData.append("usermessage=").append(URLEncoder.encode(userMessage, StandardCharsets.UTF_8.name()));
		if (systemMessage != null && !systemMessage.isEmpty()) {
			postData.append("&systemmessage=").append(URLEncoder.encode(systemMessage, StandardCharsets.UTF_8.name()));
		}
		int i = 0;
		if (parameters != null) {
			while (i < parameters.length - 1) {
				final String name = parameters[i++];
				final String value = parameters[i++];
				if (value != null) {
					postData.append('&');
					postData.append(URLEncoder.encode(name, StandardCharsets.UTF_8.name()));
					postData.append('=');
					postData.append(URLEncoder.encode(value, StandardCharsets.UTF_8.name()));
				}
			}
		}

		return postData.toString();
	}

	/**
	 * Handler for HTTP/HTTPS response.
	 */
	public interface OnHttpResponseListener {
		/**
		 * Handle HTTP/HTTPS response.
		 *
		 * @param response     The response as String.
		 * @param responseData The response as data.
		 */
		void onHttpResponse(String response, ResponseData responseData);
	}

	/**
	 * Response data from server.
	 */
	public static final class ResponseData {
		/**
		 * Success status of the call.
		 */
		private final boolean success;
		/**
		 * Error code (if not success).
		 */
		private final int errorCode;
		/**
		 * Error message (if not success).
		 */
		private final String errorMessage;
		/**
		 * Response data (if success).
		 */
		private final Map<String, Object> data;

		private ResponseData(final boolean success, final int errorCode, final String errorMessage, final Map<String, Object> data) {
			this.success = success;
			this.errorCode = errorCode;
			this.errorMessage = errorMessage;
			this.data = data;
		}

		/**
		 * Extract response data from server response.
		 *
		 * @param context  The context
		 * @param response The server response.
		 * @return The response data.
		 */
		private static ResponseData extractResponseData(final Context context, final String response) {
			try {
				JSONObject jsonObject = new JSONObject(response);
				boolean success = "success".equals(jsonObject.getString("status"));
				Map<String, Object> data = new HashMap<>();
				if (success) {
					for (Iterator<String> it = jsonObject.keys(); it.hasNext(); ) {
						String key = it.next();

						if (jsonObject.get(key) instanceof Integer) {
							data.put(key, jsonObject.getInt(key));
						}
						else if (jsonObject.get(key) instanceof Boolean) {
							data.put(key, jsonObject.getBoolean(key));
						}
						else {
							data.put(key, jsonObject.getString(key));
						}
					}
					return new ResponseData(true, 0, "", data);
				}
				else {
					int errorCode = jsonObject.getInt("errorcode");
					String errorMessage = jsonObject.getString("errormessage");
					for (Iterator<String> it = jsonObject.keys(); it.hasNext(); ) {
						String key = it.next();
						if (!"status".equals(key) && !"errorcode".equals(key) && !"errormessage".equals(key)) {
							data.put(key, jsonObject.getString(key));
						}
					}
					return new ResponseData(false, errorCode, errorMessage, data);
				}
			}
			catch (Exception e) {
				Log.e(Application.TAG, "Failed to extract response data from " + response, e);
				return new ResponseData(false, 900, "Error parsing JSON: " + e.getMessage(), new HashMap<>()); // MAGIC_NUMBER
			}
		}

		public boolean isSuccess() {
			return success;
		}

		public int getErrorCode() {
			return errorCode;
		}

		public String getErrorMessage() {
			return errorMessage;
		}

		public Map<String, Object> getData() {
			return data;
		}
	}

}
