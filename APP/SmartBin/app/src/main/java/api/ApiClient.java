package api;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ApiClient {
    // Define the base URL for the API (Replace with your actual backend URL)
    private static final String BASE_URL = "https://bromeo.pythonanywhere.com/"; // Replace with your base URL
    private static Retrofit retrofit;

    // Method to get a Retrofit instance
    public static Retrofit getClient() {
        if (retrofit == null) {
            retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL) // Set the base URL for the API
                    .addConverterFactory(GsonConverterFactory.create()) // Add Gson converter
                    .build();
        }
        return retrofit;
    }
}
