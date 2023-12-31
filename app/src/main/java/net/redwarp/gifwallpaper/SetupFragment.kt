/* Copyright 2020 Benoit Vermont
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
package net.redwarp.gifwallpaper

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.Keep
import androidx.appcompat.widget.Toolbar
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.GestureDetectorCompat
import androidx.core.view.ViewCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import dev.sasikanth.colorsheet.ColorSheet
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import net.redwarp.gifwallpaper.data.ColorScheme
import net.redwarp.gifwallpaper.data.FlowBasedModel
import net.redwarp.gifwallpaper.data.WallpaperStatus
import net.redwarp.gifwallpaper.databinding.FragmentSetupBinding
import net.redwarp.gifwallpaper.renderer.Rotation
import net.redwarp.gifwallpaper.renderer.ScaleType
import net.redwarp.gifwallpaper.renderer.drawableFlow
import net.redwarp.gifwallpaper.util.isDark
import net.redwarp.gifwallpaper.util.setStatusBarColor
import net.redwarp.gifwallpaper.util.systemWindowInsetCompatBottom

/**
 * A simple [Fragment] subclass as the second destination in the navigation.
 */
@Keep
class SetupFragment : Fragment() {
    private var currentScale = 0
    private var currentRotation = 0
    private lateinit var flowBasedModel: FlowBasedModel

    private var _binding: FragmentSetupBinding? = null
    private val binding get() = _binding!!

    private var colorInfo: ColorScheme? = null
        set(value) {
            field = value
            binding.changeColorButton.isEnabled = value != null
        }
    private var currentColor: Int? = null
    private lateinit var detector: GestureDetectorCompat
    private val getGif =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri == null) return@registerForActivityResult
            lifecycleScope.launchWhenCreated {
                flowBasedModel.loadNewGif(requireContext(), uri)
                flowBasedModel.resetTranslate()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSetupBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.openGifButton.setOnClickListener {
            pickDocument()
        }
        binding.changeScaleButton.setOnClickListener {
            changeScale()
        }
        binding.changeColorButton.setOnClickListener {
            changeColor()
        }
        binding.rotateButton.setOnClickListener {
            rotate()
        }
        binding.buttonContainer.setOnApplyWindowInsetsListener { _, insets ->
            binding.buttonContainer.updatePadding(bottom = insets.systemWindowInsetCompatBottom)
            insets
        }

        flowBasedModel = FlowBasedModel.get(requireContext())

        lifecycleScope.launchWhenStarted {
            flowBasedModel.colorInfoFlow.onEach { colorInfo ->
                this@SetupFragment.colorInfo = colorInfo as? ColorScheme
            }.launchIn(this)
            flowBasedModel.backgroundColorFlow.onEach { backgroundColor ->
                currentColor = backgroundColor
                adjustTheme(backgroundColor)
            }.launchIn(this)
            flowBasedModel.scaleTypeFlow.onEach { scaleType ->
                currentScale = scaleType.ordinal
            }.launchIn(this)
            flowBasedModel.rotationFlow.onEach { rotation ->
                currentRotation = rotation.ordinal
            }.launchIn(this)
            flowBasedModel.wallpaperStatusFlow.onEach {
                val isWallpaperSet = it is WallpaperStatus.Wallpaper
                binding.changeScaleButton.isEnabled = isWallpaperSet
                binding.rotateButton.isEnabled = isWallpaperSet
            }.launchIn(this)

            drawableFlow(
                this@SetupFragment.requireContext(),
                flowBasedModel,
                getString(R.string.click_the_open_gif_button),
                animated = true,
                isService = false
            ).onEach { drawable ->
                binding.imageView.background = drawable
            }.launchIn(this)
        }

        detector = GestureDetectorCompat(requireContext(), MyGestureListener())
        binding.touchArea.setOnTouchListener { _, event ->
            detector.onTouchEvent(event)
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.imageView) { _, inset ->
            (binding.touchArea.layoutParams as? FrameLayout.LayoutParams)?.setMargins(
                inset.systemGestureInsets.left,
                inset.systemGestureInsets.top,
                inset.systemGestureInsets.right,
                inset.systemGestureInsets.bottom,
            )

            inset
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.extras, menu)
        menu.findItem(R.id.settings).isVisible =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.clear_gif -> {
                lifecycleScope.launchWhenCreated {
                    flowBasedModel.clearGif()
                }
                return true
            }
            R.id.settings -> {
                startActivity(Intent(requireContext(), SettingsActivity::class.java))
                return true
            }
            R.id.about -> {
                startActivity(TextActivity.getIntent(requireContext(), "about.md"))
                return true
            }
            R.id.privacy -> {
                startActivity(TextActivity.getIntent(requireContext(), "privacy.md"))
                return true
            }
        }

        return super.onOptionsItemSelected(item)
    }

    private fun adjustTheme(backgroundColor: Int) {
        setStatusBarColor(backgroundColor.isDark())
        val overflowColor = if (backgroundColor.isDark()) Color.WHITE else Color.BLACK

        val toolbar: Toolbar? = (activity as? SetupActivity)?.toolbar
        val icon = toolbar?.overflowIcon?.let {
            DrawableCompat.wrap(it).also { wrapped -> wrapped.setTint(overflowColor) }
        }

        toolbar?.overflowIcon = icon
    }

    private fun pickDocument() {
        getGif.launch("image/gif")
    }

    private fun changeScale() {
        currentScale = (currentScale + 1) % ScaleType.values().size
        lifecycleScope.launchWhenCreated {
            flowBasedModel.setScaleType(ScaleType.values()[currentScale])
        }
    }

    private fun changeColor() {
        colorInfo?.let { colorInfo ->
            val colors =
                colorInfo.palette.targets.map {
                    colorInfo.palette.getColorForTarget(it, Color.BLACK)
                }.distinct().toIntArray()

            ColorSheet().colorPicker(
                colors, currentColor, noColorOption = true
            ) {
                lifecycleScope.launchWhenCreated {
                    if (it == ColorSheet.NO_COLOR) {
                        currentColor = null
                        flowBasedModel.setBackgroundColor(colorInfo.defaultColor)
                    } else {
                        currentColor = it
                        flowBasedModel.setBackgroundColor(it)
                    }
                }
            }.cornerRadius(0).show(parentFragmentManager)
        }
    }

    private fun rotate() {
        currentRotation = (currentRotation + 1) % Rotation.values().size
        lifecycleScope.launchWhenCreated {
            flowBasedModel.setRotation(Rotation.values()[currentRotation])
        }
    }

    private inner class MyGestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onScroll(
            e1: MotionEvent?,
            e2: MotionEvent?,
            distanceX: Float,
            distanceY: Float
        ): Boolean {
            lifecycleScope.launchWhenCreated {
                flowBasedModel.postTranslate(-distanceX, -distanceY)
            }
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            lifecycleScope.launchWhenCreated {
                flowBasedModel.resetTranslate()
            }
            return true
        }
    }
}
