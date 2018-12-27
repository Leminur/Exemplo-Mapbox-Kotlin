package com.pos.frederico.projetogeoreferenciado

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.widget.Toast

import com.hlab.fabrevealmenu.listeners.OnFABMenuSelectedListener

import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.constants.Style
import com.mapbox.mapboxsdk.maps.MapView

import kotlinx.android.synthetic.main.activity_main.*


class MainActivity : AppCompatActivity(), OnFABMenuSelectedListener {
    private lateinit var mapView: MapView

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
        Toast.makeText(this@MainActivity, "Passei função GPS", Toast.LENGTH_SHORT).show()
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

