/* Copyright 2020 Redwarp
 * Copyright 2020 GifWallpaper Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.redwarp.gifwallpaper.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import androidx.core.content.getSystemService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.sendBlocking
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf

fun powerSaveFlow(context: Context) =
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
        callbackFlow {
            send(context.getSystemService<PowerManager>()?.isPowerSaveMode ?: false)

            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {

                    try {
                        sendBlocking(
                            context.getSystemService<PowerManager>()?.isPowerSaveMode ?: false
                        )
                    } catch (e: Exception) {
                        @Suppress("ThrowableNotThrown")
                        cancel(CancellationException(e.message, e))
                    }
                }
            }
            context.registerReceiver(
                receiver,
                IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            )

            awaitClose {
                context.unregisterReceiver(receiver)
            }
        }
    } else {
        flowOf(false)
    }

fun thermalThrottleFlow(context: Context) =
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
        callbackFlow {
            val pm = context.getSystemService<PowerManager>()
            if (pm == null) {
                send(false)
                return@callbackFlow
            }

            val listener = PowerManager.OnThermalStatusChangedListener { thermalStatus ->
                try {
                    sendBlocking(thermalStatus >= PowerManager.THERMAL_STATUS_SEVERE)
                } catch (e: Exception) {
                    @Suppress("ThrowableNotThrown")
                    cancel(CancellationException(e.message, e))
                }
            }
            pm.addThermalStatusListener(listener)

            awaitClose {
                pm.removeThermalStatusListener(listener)
            }
        }
    } else {
        flowOf(false)
    }
