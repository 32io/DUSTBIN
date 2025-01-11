package com.example.smartbin.api;

import com.example.smartbin.models.BinStatus;
import com.example.smartbin.models.BinRegistration;
import com.example.smartbin.models.RegistrationResponse;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Body;

public interface ApiService {

    // GET request to fetch the status of a specific bin
    @GET("bins/status/{binId}")
    Call<BinStatus> getBinStatus(@Path("binId") String binId);

    // POST request to register a new bin
    @POST("bins/register")
    Call<RegistrationResponse> registerBin(@Body BinRegistration binRegistration);
}
