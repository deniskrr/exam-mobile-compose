package com.deniskrr.exam.ui

import android.content.SharedPreferences
import android.util.Log
import androidx.compose.*
import androidx.compose.frames.ModelList
import androidx.compose.frames.modelListOf
import androidx.ui.core.ContextAmbient
import androidx.ui.core.EditorModel
import androidx.ui.core.Text
import androidx.ui.input.KeyboardType
import androidx.ui.layout.*
import androidx.ui.material.AlertDialog
import androidx.ui.material.Button
import androidx.ui.material.CircularProgressIndicator
import androidx.ui.unit.dp
import com.deniskrr.exam.extensions.getErrorMessage
import com.deniskrr.exam.isConnected
import com.deniskrr.exam.model.Request
import com.deniskrr.exam.repository.Repository
import com.deniskrr.exam.repository.remote.RemoteRepository
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response


@Composable
fun MySectionScreen() {
    val sharedPreferences = ambient(key = ContextAmbient).getSharedPreferences(
        MainActivity.SHARED_PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )
    val mySectionState = remember { MySectionState(RemoteRepository(), sharedPreferences) }

    Container(padding = EdgeInsets(16.dp)) {
        MySectionContent(mySectionState)
    }

    if (mySectionState.isLoading) Center {
        CircularProgressIndicator()
    }

    if (mySectionState.errorMessage.isNotBlank()) {
        AlertDialog(
            onCloseRequest = { mySectionState.errorMessage = "" },
            title = { Text("Error!") },
            text = { Text(mySectionState.errorMessage) },
            buttons = { Button(text = "Cool", onClick = { mySectionState.errorMessage = "" }) }
        )
    }

}

@Composable
fun MySectionContent(mySectionState: MySectionState) {
    Column {
        StudentNameForm(mySectionState)
        Spacer(modifier = LayoutHeight(16.dp))
        RequestRecorder(mySectionState)
        Spacer(modifier = LayoutHeight(16.dp))
        RequestList(mySectionState)
    }
}

@Composable
fun RequestRecorder(mySectionState: MySectionState) {
    val nameEditorModel = state { EditorModel() }
    val statusEditorModel = state { EditorModel() }
    LayoutGravity
    val eCostEditorModel = state { EditorModel() }
    val costEditorModel = state { EditorModel() }
    Column {
        ExamTextField(label = "Name", editorModel = nameEditorModel)
        Spacer(modifier = LayoutHeight(8.dp))
        ExamTextField(label = "Status", editorModel = statusEditorModel)
        Spacer(modifier = LayoutHeight(8.dp))
        ExamTextField(
            label = "eCost",
            editorModel = eCostEditorModel,
            keyboardType = KeyboardType.Number
        )
        Spacer(modifier = LayoutHeight(8.dp))
        ExamTextField(
            label = "Cost",
            editorModel = costEditorModel,
            keyboardType = KeyboardType.Number
        )
        Spacer(modifier = LayoutHeight(16.dp))
        val context = ambient(key = ContextAmbient)

        Button(text = "Send request", onClick = {
            val request = Request(
                0,
                nameEditorModel.value.text,
                statusEditorModel.value.text,
                mySectionState.studentName,
                eCostEditorModel.value.text.toInt(),
                costEditorModel.value.text.toInt()
            )

            if (isConnected(context)) {
                mySectionState.offlineRequests.forEach { request ->
                    mySectionState.recordRequest(request) // Add requests that were saved in-memory last time there was no connection
                }
                mySectionState.recordRequest(request)
            } else {
                mySectionState.errorMessage = "You are not connected to Internet"
                mySectionState.recordRequestInMemory(request)
            }
        })
    }
}

@Composable
private fun RequestList(mySectionState: MySectionState) {
    Column {
        Button(text = "Refresh list", onClick = {
            mySectionState.getRequestsOfStudent(mySectionState.studentName)
        })
        mySectionState.requests.forEach { request ->
            RequestRow(request = request)
        }
    }
}

@Composable
fun StudentNameForm(mySectionState: MySectionState) {
    val studentName = mySectionState.studentName
    val studentNameEditor = state { EditorModel(studentName) }
    Column {
        ExamTextField(label = "Student name", editorModel = studentNameEditor)

        Spacer(modifier = LayoutHeight(8.dp))

        Button(text = "Save name", onClick = {
            mySectionState.studentName = studentNameEditor.value.text
        })
    }
}

@Model
class MySectionState(
    private val repository: Repository,
    private val sharedPreferences: SharedPreferences
) {
    companion object {
        const val TAG = "MySectionState"
        const val STUDENT_PREFS_KEY = "Student"
    }

    var errorMessage: String = ""
    var isLoading: Boolean = false
    var studentName: String
        get() = sharedPreferences.getString(STUDENT_PREFS_KEY, "")!!
        set(value) =
            sharedPreferences.edit().putString(STUDENT_PREFS_KEY, value).apply()

    val offlineRequests: MutableList<Request> = mutableListOf()
    val requests: ModelList<Request> = modelListOf()

    fun recordRequestInMemory(request: Request) {
        isLoading = true
        offlineRequests.add(request)
        isLoading = false
    }

    fun deleteRequestFromMemory(request: Request) {
        isLoading = true
        offlineRequests.remove(request)
        isLoading = false
    }

    fun recordRequest(request: Request) {
        isLoading = true
        repository.recordRequest(request, object : Callback<Request> {
            override fun onFailure(call: Call<Request>, t: Throwable) {
                Log.e(TAG, "Error recording request", t)
                isLoading = false
            }

            override fun onResponse(call: Call<Request>, response: Response<Request>) {
                if (response.isSuccessful) {
                    val responseRequest = response.body()!!
                    Log.d(TAG, "Successfully recorded request ${responseRequest.name}")
                } else {
                    val responseErrorMessage = response.getErrorMessage()
                    errorMessage = responseErrorMessage
                    Log.d(
                        TAG,
                        "Failed recording request ${request.name}. Error: $responseErrorMessage"
                    )
                    deleteRequestFromMemory(request)
                }
                isLoading = false
            }
        })
    }

    fun getRequestsOfStudent(studentName: String) {
        isLoading = true
        repository.getRequestsOfStudent(studentName, object : Callback<List<Request>> {
            override fun onFailure(call: Call<List<Request>>, t: Throwable) {
                Log.e(TAG, "Error getting requests of student", t)
                isLoading = false
            }

            override fun onResponse(call: Call<List<Request>>, response: Response<List<Request>>) {
                if (response.isSuccessful) {
                    val responseRequests = response.body()!!

                    with(requests) {
                        clear()
                        addAll(responseRequests)
                    }

                    Log.d(TAG, "Successfully retrieved requests of $studentName")
                } else {
                    val responseErrorMessage = response.getErrorMessage()
                    errorMessage = responseErrorMessage
                    Log.d(
                        TAG,
                        "Failed retrieving requests of $studentName. . Error: $responseErrorMessage"
                    )
                }
                isLoading = false
            }
        })
    }
}
