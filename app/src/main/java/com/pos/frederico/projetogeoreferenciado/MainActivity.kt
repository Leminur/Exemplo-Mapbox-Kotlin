package com.pos.frederico.projetogeoreferenciado

import android.annotation.SuppressLint
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.widget.Toast

import com.hlab.fabrevealmenu.listeners.OnFABMenuSelectedListener
import com.mapbox.android.core.permissions.PermissionsListener
import com.mapbox.android.core.permissions.PermissionsManager

import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.constants.Style
import com.mapbox.mapboxsdk.location.LocationComponent
import com.mapbox.mapboxsdk.location.LocationComponentOptions
import com.mapbox.mapboxsdk.location.modes.CameraMode
import com.mapbox.mapboxsdk.location.modes.RenderMode
import com.mapbox.mapboxsdk.maps.MapView
import com.mapbox.mapboxsdk.maps.MapboxMap

import kotlinx.android.synthetic.main.activity_main.*


class MainActivity : AppCompatActivity(), OnFABMenuSelectedListener, PermissionsListener {
    private lateinit var mapView: MapView
    private var permissionsManager: PermissionsManager? = null
    private lateinit var mapboxMap: MapboxMap
    private var locationComponent: LocationComponent? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //Uso do token Mapbox
        Mapbox.getInstance(this, getString(R.string.string_acesso_token))
        setContentView(R.layout.activity_main)

        //Chamada e definição do mapview
        mapView = findViewById(R.id.mapView)
        mapView.onCreate(savedInstanceState)

        //Dados valores padrões do mapbox
        mapView.getMapAsync {
            it.setStyle(Style.TRAFFIC_DAY)
            mapboxMap = it

//            val iconFactory: IconFactory = IconFactory.getInstance(this@MainActivity)
//            val icon: Icon = iconFactory.fromResource(R.drawable.mapbox_logo_icon)
//            lateinit var testando: MarkerOptions
//            testando.position(LatLng(-15.76062, -47.87053))
//            testando.title("Teste")
//            testando.icon(icon)
//            testando.snippet("Passei")
//            it.addMarker(testando)

        }

        //Definindo funções do menu e do FAB
        try {
            menuPrincipal.setMenu(R.menu.fab_janela)
            menuPrincipal.bindAnchorView(botaoFabMenu)
            menuPrincipal.setOnFABMenuSelectedListener(this@MainActivity)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        //Botão GPS Principal
        fabGPS.setOnClickListener {
            fabGPSFuncao()
        }

    }

    override fun onMenuItemSelected(view: View, id: Int) {
        when (id) {
            R.id.menu_Mapas -> menuMapa()
            R.id.menu_3D -> menu3D()
            R.id.menu_Rotas -> menuRotas()
        }
    }


    // Funções de acesso dos botões do mapa
    private fun fabGPSFuncao() {
        // Checar se as permissões foram habilitadas, e se não, fazer as requisições delas.
        if (PermissionsManager.areLocationPermissionsGranted(this)) {
            inicializaGPS()
        } else {
            permissionsManager = PermissionsManager(this)
            permissionsManager!!.requestLocationPermissions(this)
        }

    }

    // Habilita o modo GPS
    @SuppressLint("MissingPermission")
    private fun inicializaGPS() {
        if (locationComponent == null || !locationComponent!!.isLocationComponentEnabled) {
            val options: LocationComponentOptions = LocationComponentOptions.builder(this)
                .trackingGesturesManagement(true)
                .accuracyColor(ContextCompat.getColor(this, R.color.padrao_azul_menu_200))
                .build()

            // Setar uma instancia do componente
            locationComponent = mapboxMap.locationComponent
            locationComponent!!.activateLocationComponent(this, options)

            // Enable to make component visible
            locationComponent!!.isLocationComponentEnabled = true

            // Set the component's camera mode
            locationComponent!!.cameraMode = CameraMode.TRACKING
            locationComponent!!.renderMode = RenderMode.COMPASS
            fabGPS.setImageDrawable(ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_gps_fixed_24dp))
        } else {
            locationComponent!!.isLocationComponentEnabled = false
            fabGPS.setImageDrawable(ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_gps_not_fixed_24dp))
        }

    }


    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        permissionsManager?.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun onExplanationNeeded(permissionsToExplain: MutableList<String>?) {
        //Inserir dados para avisar o usuário
    }

    override fun onPermissionResult(granted: Boolean) {
        if (granted) {
            fabGPSFuncao()
        } else {
            //Caso o usuário recusar as permissões, a função abaixo será ser executado
            val snackbar = Snackbar.make(root_layout, R.string.permissao_explicacao, Snackbar.LENGTH_INDEFINITE)
            snackbar.setAction("Fechar") {
                snackbar.dismiss()
            }
            snackbar.show()
        }
    }

    private fun menuMapa() {
        Toast.makeText(this@MainActivity, "Attachment Selected", Toast.LENGTH_SHORT).show()
    }

    private fun menu3D() {
        Toast.makeText(this@MainActivity, "Image Selected", Toast.LENGTH_SHORT).show()
    }

    private fun menuRotas() {
        Toast.makeText(this@MainActivity, "Place Selected", Toast.LENGTH_SHORT).show()
    }

    // Funções do mapView com cada status do app

    public override fun onStart() {
        super.onStart()
        mapView.onStart()
    }

    public override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    public override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    public override fun onStop() {
        super.onStop()
        mapView.onStop()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }


}

