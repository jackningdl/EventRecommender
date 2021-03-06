package external;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import entity.Item;
import entity.Item.ItemBuilder;

public class TicketMasterAPI {
	private static final String URL = "https://app.ticketmaster.com/discovery/v2/events.json";
	private static final String DEFAULT_KEYWORD = "";
	// from TicketMaser
	private static final String API_KEY = "KAIALFNsDitUZR3QAnuitMQEehmmBauB";

	private static final String EMBEDDED = "_embedded";
	private static final String EVENTS = "events";
	private static final String NAME = "name";
	private static final String ID = "id";
	private static final String URL_STR = "url";
	private static final String RATING = "rating";
	private static final String DISTANCE = "distance";
	private static final String VENUES = "venues";
	private static final String ADDRESS = "address";
	private static final String LINE1 = "line1";
	private static final String LINE2 = "line2";
	private static final String LINE3 = "line3";
	private static final String CITY = "city";
	private static final String IMAGES = "images";
	private static final String CLASSIFICATIONS = "classifications";
	private static final String SEGMENT = "segment";



	//首先确定response返回的是JSON，这里的keyword就是要查询的输入string
	public List<Item> search(double lat, double lon, String keyword) {
		//Encode keyword in URL 因为有可能有特殊字符
		if (keyword == null) {
			keyword = DEFAULT_KEYWORD;
		}
		try {
			keyword = java.net.URLEncoder.encode(keyword,  "UTF-8"); // 
		} catch (Exception e) {
			e.printStackTrace();
		}

		//转化lat 和 lon, base32是不丢失信息的，所以完全可以反向hash
		String geoHash = GeoHash.encodeGeohash(lat, lon, 8); // 数字就是保留的精度 precision 一般 8 就够了 

		// Make URL "apikey = 12345&geopoint=abcd&keyword=music&radius=50"
		String query = String.format("apikey=%s&geopoint=%s&keyword=%s&radius=%s", API_KEY, geoHash, keyword, 50);
//		String query = String.format("apikey=%s&postalCode=%s&keyword=%s&radius=%d", API_KEY,"53703",keyword, 50);

		try {
			// open a HTTP connection between java app and TicketMaster based URL
			// https://app.ticketmaster.com/discovery/v2/events.json?apikey=12345&geoPoint=abcd&keyword=music&radius=50

			HttpURLConnection connection = (HttpURLConnection) new URL(URL + "?" + query).openConnection();
			// 虽然默认，但也可以自己手动设置一下
			connection.setRequestMethod("GET");

			// 向TicketMaster发起请求，并获得response
			int responseCode = connection.getResponseCode();

			System.out.println("\nSending 'Get' request to URL: " + URL + "?" + query);
			System.out.println("Response code: " + responseCode);

			// Now read response body to get events data
			StringBuilder response = new StringBuilder();
			// reader实现了 close，它会自动调用close
			try(BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
				String inputLine;
				while ((inputLine = in.readLine()) != null) {
					response.append(inputLine);
				}
			}

			JSONObject obj = new JSONObject(response.toString());
			//如果不存在里面有 embedded的，就返回一个空的JSONArray
			if (obj.isNull("_embedded")) {
				return new ArrayList<>();
			}

			// 如果存在，就转换成event
			JSONObject embedded = obj.getJSONObject("_embedded");
			JSONArray events = embedded.getJSONArray("events");

			return getItemList(events);


		} catch (Exception e) {
			e.printStackTrace();
		}

		return new ArrayList<>();
	}


	/**
	 * Helper methods
	 */

	//  {
	//    "name": "laioffer",
	//    "id": "12345",
	//    "url": "www.laioffer.com",
	//    ...
	//    "_embedded": {
	//	    "venues": [
	//	        {
	//		        "address": {
	//		           "line1": "101 First St,",
	//		           "line2": "Suite 101",
	//		           "line3": "...",
	//		        },
	//		        "city": {
	//		        	"name": "San Francisco"
	//		        }
	//		        ...
	//	        },
	//	        ...
	//	    ]
	//    }
	//    ...
	//  }
	// TicketMaster里本身API就很凌乱
	// Address应该是隐藏的结构最深的
	private String getAddress(JSONObject event) throws JSONException {
		if (!event.isNull(EMBEDDED)) {
			JSONObject embedded = event.getJSONObject(EMBEDDED);

			if (!embedded.isNull(VENUES)) {
				JSONArray venues = embedded.getJSONArray(VENUES);

				for (int i = 0; i < venues.length(); ++i) {
					JSONObject venue = venues.getJSONObject(i);
					StringBuilder sb = new StringBuilder();
					if (!venue.isNull(ADDRESS)) {
						JSONObject address = venue.getJSONObject(ADDRESS);

						if (!address.isNull(LINE1)) {
							sb.append(address.getString(LINE1));
						}
						if (!address.isNull(LINE2)) {
							sb.append('\n');
							sb.append(address.getString(LINE2));
						}
						if (!address.isNull(LINE3)) {
							sb.append('\n');
							sb.append(address.getString(LINE3));
						}
					}

					if (!venue.isNull(CITY)) {
						JSONObject city = venue.getJSONObject(CITY);

						if (!city.isNull(NAME)) {
							sb.append('\n');
							sb.append(city.getString(NAME));
						}
					}

					String addr = sb.toString();
					if (!addr.equals("")) {
						return addr;
					}
				}
			}
		}

		return ""; // 如果没有地址，返回空string
	}

	// {"images": [{"url": "www.example.com/my_image.jpg"}, ...]}
	private String getImageUrl(JSONObject event) throws JSONException {
		if (!event.isNull(IMAGES)) {
			JSONArray images = event.getJSONArray(IMAGES);

			for (int i = 0; i < images.length(); i++) {
				JSONObject image = images.getJSONObject(i);
				StringBuilder sBuilder = new StringBuilder();
				if (!image.isNull(URL_STR)) {
					return image.getString(URL_STR);
				}
			}
		}		
		return "";
	}


	// {"classifications" : [{"segment": {"name": "music"}}, ...]}
	// classification 里面只找segment，segment里面只找name
	private Set<String> getCategories(JSONObject event) throws JSONException {
		Set<String> categories = new HashSet<>();

		if (!event.isNull(CLASSIFICATIONS)) {
			JSONArray classifications = event.getJSONArray(CLASSIFICATIONS);

			for (int i = 0; i < classifications.length(); ++i) {
				JSONObject classification = classifications.getJSONObject(i);

				if (!classification.isNull(SEGMENT)) {
					JSONObject segment = classification.getJSONObject(SEGMENT);

					if (!segment.isNull(NAME)) {
						categories.add(segment.getString(NAME));
					}
				}
			}
		}
		return categories;
	}

	// Convert JSONArray to a list of item objects.
	private List<Item> getItemList(JSONArray events) throws JSONException {
		List<Item> itemList = new ArrayList<>();

		for (int i = 0; i < events.length(); ++i) {
			JSONObject event = events.getJSONObject(i);

			ItemBuilder builder = new ItemBuilder();

			if (!event.isNull(NAME)) {
				builder.setName(event.getString(NAME));
			}
			if (!event.isNull(ID)) {
				builder.setItemId(event.getString(ID));
			}
			if (!event.isNull(URL_STR)) {
				builder.setUrl(event.getString(URL_STR));
			}
			if (!event.isNull(RATING)) {
				builder.setRating(event.getDouble(RATING));
			}
			if (!event.isNull(DISTANCE)) {
				builder.setDistance(event.getDouble(DISTANCE));
			}

			builder.setAddress(getAddress(event));
			builder.setCategories(getCategories(event));
			builder.setImageUrl(getImageUrl(event));

			//把爬下来的东西赋值到新建的builder（新建了一个item）里
			itemList.add(builder.build());
		}
		return itemList;
	}

	// 下面这个函数主要用来检测search JSON是否会成功
	private void queryAPI(double lat, double lon) {
		//JSONArray events = search(lat, lon, null); 
		List<Item> itemList = search(lat, lon, null); // 为什么后面要加null
		try {
			for (Item item: itemList) {
				//JSONObject event = events.getJSONObject(i);
				JSONObject jsonObject = item.toJSONObject();
				System.out.println(jsonObject);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Main entry for sample TicketMaster API requests.
	 */
	public static void main(String[] args) {
		TicketMasterAPI tmApi = new TicketMasterAPI();
		// Mountain View, CA
		// tmApi.queryAPI(37.38, -122.08);
		// London, UK
		 tmApi.queryAPI(51.503364, -0.12);
		// Houston, TX
		//tmApi.queryAPI(51.503364, -0.12);
	}

}




