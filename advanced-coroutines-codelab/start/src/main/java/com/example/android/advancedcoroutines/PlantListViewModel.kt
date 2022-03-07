/*
 * Copyright 2018 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.advancedcoroutines

import androidx.lifecycle.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * The [ViewModel] for fetching a list of [Plant]s.
 */
class PlantListViewModel internal constructor(
    private val plantRepository: PlantRepository
) : ViewModel() {

    /**
     * Request a snackbar to display a string.
     *
     * This variable is private because we don't want to expose [MutableLiveData].
     *
     * MutableLiveData allows anyone to set a value, and [PlantListViewModel] is the only
     * class that should be setting values.
     */
    private val _snackbar = MutableLiveData<String?>()

    /**
     * Request a snackbar to display a string.
     */
    val snackbar: LiveData<String?>
        get() = _snackbar

    private val _spinner = MutableLiveData<Boolean>(false)
    /**
     * Show a loading spinner if true
     */
    val spinner: LiveData<Boolean>
        get() = _spinner

    /**
     * LiveData
     */
    private val growZone = MutableLiveData<GrowZone>(NoGrowZone)

    val plants: LiveData<List<Plant>> = growZone.switchMap { growZone ->
        if (growZone == NoGrowZone) {
            plantRepository.plants
        } else {
            plantRepository.getPlantsWithGrowZone(growZone)
        }
    }

    /**
     * Flow
     *
     * .asLiveData() converts Flow to LiveData with timeout. Configuration change is handled:
     *  if observation occurs before timeout then flow continues, else flow canceled.
     *
     * MutableStateFlow is very similar to LiveData in that it holds only the single, latest value.
     */
    private val growZoneFlow = MutableStateFlow<GrowZone>(NoGrowZone)

    /**
     * StateFlow is different from a regular flow created using, for example, the flow{} builder.
     * A StateFlow is created with an initial value and keeps its state even without being collected
     * and between subsequent collections. You can use the MutableStateFlow interface (as shown above)
     * to change the value (state) of a StateFlow.

     * You will often find that flows that behave like StateFlow are called hot, as opposed to regular,
     * cold flows which only execute when they're collected.
     *
     * Flow's flatMapLatest extensions allow you to switch between multiple flows.
     */
    val plantsUsingFlow: LiveData<List<Plant>> = growZoneFlow.flatMapLatest { growZone ->
        if (growZone == NoGrowZone) {
            plantRepository.plantsFlow
        } else {
            plantRepository.getPlantsWithGrowZoneFlow(growZone)
        }
    }.asLiveData()

    // Using Flow, it's natural to collect data in the ViewModel, Repository,
    // or other data layers when needed.
    // Since Flow is not tied to the UI, you don't need a UI observer to
    // collect a flow. This is a big difference from LiveData which always
    // requires a UI-observer to run. It is not a good idea to try to observe
    // a LiveData in your ViewModel because it doesn't have an appropriate
    // observation lifecycle.
    init {
        clearGrowZoneNumber()
        loadDataFor(growZoneFlow) {
            if (it == NoGrowZone) { plantRepository.tryUpdateRecentPlantsCache() }
            else { plantRepository.tryUpdateRecentPlantsForGrowZoneCache(it) }
        }
    }

    fun <T> loadDataFor(
        source: StateFlow<T>,
        block: suspend (T) -> Unit) {

        _spinner.value = true
        source
            .mapLatest(block)
            .onEach {  _spinner.value = false } // called whenever flow above emits
            .catch { throwable ->  _snackbar.value = throwable.message } // catch exceptions thrown above
            .launchIn(viewModelScope) // collect flow in here in viewmodel
    }

    /**
     * Filter the list to this grow zone.
     *
     * In the starter code version, this will also start a network request. After refactoring,
     * updating the grow zone will automatically kickoff a network request.
     */
    fun setGrowZoneNumber(num: Int) {
        growZone.value = GrowZone(num)
        growZoneFlow.value = GrowZone(num)
    }

    /**
     * Clear the current filter of this plants list.
     *
     * In the starter code version, this will also start a network request. After refactoring,
     * updating the grow zone will automatically kickoff a network request.
     */
    fun clearGrowZoneNumber() {
        growZone.value = NoGrowZone
        growZoneFlow.value = NoGrowZone
    }

    /**
     * Return true if the current list is filtered.
     */
    fun isFiltered() = growZone.value != NoGrowZone

    /**
     * Called immediately after the UI shows the snackbar.
     */
    fun onSnackbarShown() {
        _snackbar.value = null
    }

    /**
     * Helper function to call a data load function with a loading spinner; errors will trigger a
     * snackbar.
     *
     * By marking [block] as [suspend] this creates a suspend lambda which can call suspend
     * functions.
     *
     * @param block lambda to actually load data. It is called in the viewModelScope. Before calling
     *              the lambda, the loading spinner will display. After completion or error, the
     *              loading spinner will stop.
     */
    private fun launchDataLoad(block: suspend () -> Unit): Job {
        return viewModelScope.launch {
            try {
                _spinner.value = true
                block()
            } catch (error: Throwable) {
                _snackbar.value = error.message
            } finally {
                _spinner.value = false
            }
        }
    }
}
