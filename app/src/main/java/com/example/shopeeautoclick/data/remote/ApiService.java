package com.example.shopeeautoclick.data.remote;

import com.example.shopeeautoclick.data.model.DoneRequest;
import com.example.shopeeautoclick.data.model.ErrorRequest;
import com.example.shopeeautoclick.data.model.LiveTask;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;

public interface ApiService {
    @GET("tasks/pending")
    Call<LiveTask> getPendingTask();

    @POST("tasks/{id}/processing")
    Call<Void> markProcessing(@Path("id") String taskId);

    @POST("tasks/{id}/done")
    Call<Void> markDone(@Path("id") String taskId, @Body DoneRequest request);

    @POST("tasks/{id}/error")
    Call<Void> markError(@Path("id") String taskId, @Body ErrorRequest request);
}
