package com.karadaglabs.cartv

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment

/**
 * CarStream hosts YouTube by adding a support fragment to the
 * MATCH_PARENT FrameLayout inflated into CarActivity.g. Jetifier
 * maps aauto.aar's FragmentManager onto AndroidX, so this fragment
 * receives CarActivity lifecycle from gms.car.e FragmentController.
 */
class HostedProjectionFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val car = inflater.context as? CarVideoActivity
            ?: context as? CarVideoActivity
            ?: error("HostedProjectionFragment requires CarVideoActivity")
        return car.buildHostedPage()
    }
}
