package com.example.shopeeautoclick.data.repository;

import android.content.Context;

import com.example.shopeeautoclick.data.model.DoneRequest;
import com.example.shopeeautoclick.data.model.ErrorRequest;
import com.example.shopeeautoclick.data.model.LiveTask;
import com.example.shopeeautoclick.data.remote.ApiService;
import com.example.shopeeautoclick.data.remote.RetrofitClient;

import java.io.IOException;

import retrofit2.Response;

public class TaskRepository {
    private final ApiService apiService;

    public TaskRepository(Context context) {
        apiService = RetrofitClient.getApiService(context.getApplicationContext());
    }

    public LiveTask getPendingTask() throws IOException {
        Response<LiveTask> response = apiService.getPendingTask().execute();
        ensureSuccessful(response, "GET /tasks/pending");

        LiveTask task = response.body();
        if (task == null || task.isNoTask() || task.getId() == null || task.getId().isEmpty()) {
            return null;
        }
        return task;
    }

    public void markProcessing(String taskId) throws IOException {
        Response<Void> response = apiService.markProcessing(taskId).execute();
        ensureSuccessful(response, "POST /tasks/" + taskId + "/processing");
    }

    public void markDone(String taskId, int collectedCoin) throws IOException {
        Response<Void> response = apiService.markDone(taskId, new DoneRequest(collectedCoin)).execute();
        ensureSuccessful(response, "POST /tasks/" + taskId + "/done");
    }

    public void markError(String taskId, String errorMessage) throws IOException {
        Response<Void> response = apiService.markError(taskId, new ErrorRequest(errorMessage)).execute();
        ensureSuccessful(response, "POST /tasks/" + taskId + "/error");
    }

    private void ensureSuccessful(Response<?> response, String action) throws IOException {
        if (!response.isSuccessful()) {
            throw new IOException(action + " failed with HTTP " + response.code());
        }
    }
}
