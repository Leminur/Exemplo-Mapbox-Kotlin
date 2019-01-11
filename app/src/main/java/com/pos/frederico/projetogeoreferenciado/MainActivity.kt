package com.pos.frederico.projetogeoreferenciado

import android.annotation.SuppressLint
import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.graphics.Color
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v4.content.ContextCompat
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.widget.Toast

import com.hlab.fabrevealmenu.listeners.OnFABMenuSelectedListener

import com.mapbox.android.core.location.LocationEngine
import com.mapbox.android.core.location.LocationEngineListener
import com.mapbox.android.core.location.LocationEnginePriority
import com.mapbox.android.core.location.LocationEngineProvider
import com.mapbox.android.core.permissions.PermissionsListener
import com.mapbox.android.core.permissions.PermissionsManager
import com.mapbox.api.directions.v5.DirectionsCriteria
import com.mapbox.api.directions.v5.models.DirectionsResponse
import com.mapbox.api.directions.v5.models.DirectionsRoute
import com.mapbox.geojson.Point
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.annotations.Marker
import com.mapbox.mapboxsdk.annotations.MarkerOptions
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory
import com.mapbox.mapboxsdk.constants.Style
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.geometry.LatLngBounds
import com.mapbox.mapboxsdk.location.LocationComponent
import com.mapbox.mapboxsdk.location.LocationComponentOptions
import com.mapbox.mapboxsdk.location.modes.CameraMode
import com.mapbox.mapboxsdk.location.modes.RenderMode
import com.mapbox.mapboxsdk.maps.MapView
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.style.expressions.Expression.*
import com.mapbox.mapboxsdk.style.layers.FillExtrusionLayer
import com.mapbox.mapboxsdk.style.layers.PropertyFactory.*
import com.mapbox.services.android.navigation.v5.navigation.MapboxNavigation
import com.mapbox.services.android.navigation.v5.navigation.NavigationRoute
import com.mapbox.services.android.navigation.ui.v5.route.NavigationMapRoute
import com.mapbox.services.android.navigation.ui.v5.NavigationLauncher
import com.mapbox.services.android.navigation.ui.v5.NavigationLauncherOptions

import kotlinx.android.synthetic.main.activity_main.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import timber.log.Timber
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity(), OnFABMenuSelectedListener, PermissionsListener, LocationEngineListener {
    private lateinit var mapView: MapView
    private var permissionsManager: PermissionsManager? = null
    private lateinit var mapboxMap: MapboxMap
    private var locationComponent: LocationComponent? = null
    private var navigation: MapboxNavigation? = null
    private var origem: Location? = null
    private var destino: LatLng? = null
    private var locationEngine: LocationEngine? = null
    private var navigationMapRoute: NavigationMapRoute? = null
    private var currentRoute: DirectionsRoute? = null
    private var marcadorDestino: Marker? = null
    private var verificaRota: Boolean? = null
    private lateinit var meioLocomocao: String
    private var verificaOn3D: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //Uso do token Mapbox nps modulos
        Mapbox.getInstance(this, getString(R.string.string_acesso_token))
        navigation = MapboxNavigation(this@MainActivity, getString(R.string.string_acesso_token))

        setContentView(R.layout.activity_main)

        //Chamada e definição do mapview
        mapView = findViewById(R.id.mapView)
        mapView.onCreate(savedInstanceState)

        //Dados valores padrões do mapbox
        meioLocomocao = DirectionsCriteria.PROFILE_DRIVING
        mapView.getMapAsync { it ->
            it.setStyle(Style.TRAFFIC_DAY)
            mapboxMap = it

            //Funcao que ocorre ao segurar o dedo no mapa
            it.addOnMapLongClickListener { passaIt: LatLng ->
                if (PermissionsManager.areLocationPermissionsGranted(this)) {
                    destino = passaIt

                    if (locationComponent == null || !locationComponent!!.isLocationComponentEnabled) {
                        fabGPSFuncao()
                        inicializaLocationEngine()
                    }

                    val origemPonto = Point.fromLngLat(origem!!.longitude, origem!!.latitude)
                    val destinoPonto = Point.fromLngLat(destino!!.longitude, destino!!.latitude)

                    if (origem != null) {
                        procurarRota(origemPonto, destinoPonto)
                    }

                    marcadorDestino?.let {
                        mapboxMap.removeMarker(it)
                    }

                    marcadorDestino = mapboxMap.addMarker(
                        MarkerOptions().position(destino)
                    )

                } else {
                    permissionsManager = PermissionsManager(this)
                    permissionsManager!!.requestLocationPermissions(this)
                }
            }

            //Botao ao navegar a rota
            botaoNavegar.setOnClickListener {
                val simulateRoute = false

                val options = NavigationLauncherOptions.builder()
                    .directionsRoute(currentRoute)
                    .shouldSimulateRoute(simulateRoute)
                    .build()
                // Chamar esse metodo usando o context dentro da Activity ativa
                NavigationLauncher.startNavigation(this@MainActivity, options)
            }

            //Botao ao clicar o X para cancelar a rota
            cancelaRota.setOnClickListener {
                retiraRota()
            }


//            val iconFactory: IconFactory = IconFactory.getInstance(this@MainActivity)
//            val icon: Icon = iconFactory.fromResource(R.drawable.mapbox_logo_icon)
//            lateinit var testando: MarkerOptions
//            testando.position(LatLng(-15.76062, -47.87053))
//            testando.title("Teste")
//            testando.icon(icon)
//            testando.snippet("Passei")
//            it.addMarker(testando)

        }

        //Botão de funções do menu e do FAB
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
            R.id.menu_PIP -> menuPIP()
        }
    }

    private fun procurarRota(origemPonto: Point, destinoPonto: Point) {
        carregaRota.visibility = View.VISIBLE
        NavigationRoute.builder(this@MainActivity)
            .accessToken(getString(R.string.string_acesso_token))
            .origin(origemPonto)
            .destination(destinoPonto)
            .profile(meioLocomocao)
            .voiceUnits(DirectionsCriteria.METRIC)
            .build()
            .getRoute(object : Callback<DirectionsResponse> {
                override fun onResponse(call: Call<DirectionsResponse>, response: Response<DirectionsResponse>) {
                    // Pega infomação da resposta generica HTTP
                    Timber.d("Código de resposta: %s", response.code())
                    if (response.body() == null) {
                        Timber.e("Sem rotas encontradas! Tenha certeza que você configurou o usuário e o token de acesso.")
                        return
                    } else if (response.body()!!.routes().isEmpty()) {
                        Timber.e("Nenhuma rota foi encontrada com sucesso.")
                        Toast.makeText(this@MainActivity, "Nenhuma rota encontrada!", Toast.LENGTH_SHORT).show()
                        return
                    }

                    verificaRota = true

                    currentRoute = response.body()!!.routes()[0]

                    // Desenha a rota no mapa
                    if (navigationMapRoute != null) {
                        navigationMapRoute!!.updateRouteArrowVisibilityTo(false)
                        navigationMapRoute!!.updateRouteVisibilityTo(false)
                    } else {
                        navigationMapRoute = NavigationMapRoute(null, mapView, mapboxMap, R.style.NavigationMapRoute)
                    }
                    navigationMapRoute!!.addRoute(currentRoute)

                    // Transformando dados da distancia em km
                    val textoDistancia = currentRoute!!.distance()!! / 1000
                    println("Formato do numero: $textoDistancia")
                    val texto2Distancia = String.format("%.2f", textoDistancia) + " KM"
                    distanciaRota.text = texto2Distancia

                    // Transformando dados do tempo no padrão
                    val passaTempo = String.format(
                        "%02d:%02d:%02d",
                        TimeUnit.SECONDS.toHours(currentRoute!!.duration()!!.toLong()),
                        TimeUnit.SECONDS.toMinutes(currentRoute!!.duration()!!.toLong()) -
                                TimeUnit.HOURS.toMinutes(TimeUnit.SECONDS.toHours(currentRoute!!.duration()!!.toLong())),
                        TimeUnit.SECONDS.toSeconds(currentRoute!!.duration()!!.toLong()) -
                                TimeUnit.MINUTES.toSeconds(TimeUnit.SECONDS.toMinutes(currentRoute!!.duration()!!.toLong()))
                    )
                    tempoRota.text = passaTempo
                    println("Total:" + currentRoute!!.duration()!!.toString())

                    //Mostrar os botões de navegação ao encontrar a rota
                    botaoNavegar.isEnabled = true
                    botaoNavegar.visibility = View.VISIBLE
                    cancelaRota.isEnabled = true
                    cancelaRota.visibility = View.VISIBLE
                    tabelaRota.visibility = View.VISIBLE

                    carregaRota.visibility = View.INVISIBLE

                    movePosicaoRota()

                }

                override fun onFailure(call: Call<DirectionsResponse>, t: Throwable) {
                    Toast.makeText(
                        this@MainActivity,
                        "Não foi possível buscar uma rota! Por favor, verifique se você possuí uma conexão de dados ativa ou tente novamente.",
                        Toast.LENGTH_LONG
                    ).show()
                    Timber.e("Erro:$t")

                    carregaRota.visibility = View.INVISIBLE

                    if (navigationMapRoute != null) {
                        retiraRota()
                    }
                }
            })
    }

    // Funções de acesso dos botões do mapa
    private fun fabGPSFuncao() {
        // Checar se as permissões foram habilitadas, e se não, fazer as requisições delas.
        if (PermissionsManager.areLocationPermissionsGranted(this)) {
            inicializaGPS()
            inicializaLocationEngine()
        } else {
            permissionsManager = PermissionsManager(this)
            permissionsManager!!.requestLocationPermissions(this)
        }

    }

    // Habilita o modo GPS
    @SuppressLint("MissingPermission")
    private fun inicializaGPS() {
        if (locationComponent == null || !locationComponent!!.isLocationComponentEnabled) {

            locationEngine = LocationEngineProvider(this@MainActivity).obtainBestLocationEngineAvailable()
            navigation?.locationEngine = locationEngine!!

            val options: LocationComponentOptions = LocationComponentOptions.builder(this)
                .trackingGesturesManagement(true)
                .accuracyColor(ContextCompat.getColor(this, R.color.padrao_azul_menu_200))
                .build()

            // Setar uma instancia do componente
            locationComponent = mapboxMap.locationComponent
            locationComponent!!.activateLocationComponent(this, options)
            // Habilita a visibilidade do componente
            locationComponent!!.isLocationComponentEnabled = true
            // Definir o modo da camera do componente
            locationComponent!!.cameraMode = CameraMode.TRACKING
            locationComponent!!.renderMode = RenderMode.COMPASS
            fabGPS.setImageDrawable(ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_gps_fixed_24dp))
        } else {
            locationComponent!!.isLocationComponentEnabled = false
            fabGPS.setImageDrawable(ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_gps_not_fixed_24dp))
        }
    }

    @SuppressLint("MissingPermission")
    private fun inicializaLocationEngine() {
        locationEngine = LocationEngineProvider(this@MainActivity).obtainBestLocationEngineAvailable()
        locationEngine!!.priority = LocationEnginePriority.HIGH_ACCURACY
        locationEngine!!.activate()

        val lastLocation = locationEngine!!.lastLocation
        if (lastLocation != null) {
            origem = lastLocation
            moveCameraPosicao(lastLocation)
        } else {
            locationEngine!!.addLocationEngineListener(this@MainActivity)
        }
    }

    private fun moveCameraPosicao(location: Location) {
        mapboxMap.animateCamera(
            CameraUpdateFactory.newLatLngZoom(
                LatLng(location.latitude, location.longitude),
                17.0
            )
        )
    }

    private fun movePosicaoRota() {
        val origemLatLng = LatLng(origem!!.latitude, origem!!.longitude)

        val latLngBounds: LatLngBounds = LatLngBounds.Builder()
            .include(destino!!) // Northeast
            .include(origemLatLng) // Southwest
            .build()

        mapboxMap.easeCamera(CameraUpdateFactory.newLatLngBounds(latLngBounds, 300), 2000)

    }

    //Função que zera a rota e volta a tela inicial
    private fun retiraRota() {
        navigationMapRoute!!.updateRouteArrowVisibilityTo(false)
        navigationMapRoute!!.updateRouteVisibilityTo(false)

        botaoNavegar.isEnabled = false
        botaoNavegar.visibility = View.GONE
        cancelaRota.isEnabled = false
        cancelaRota.visibility = View.GONE
        tabelaRota.visibility = View.GONE

        marcadorDestino?.let {
            mapboxMap.removeMarker(it)
        }

        verificaRota = false
    }

    //Funções que gerenciam a requisição das permissões
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        permissionsManager?.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun onExplanationNeeded(permissionsToExplain: MutableList<String>?) {
        //Inserir dados para avisar o usuário antes da decisão de aceitar ou não ser feita
    }

    //Função que gerencia o que acontece quando o usuário dá permissões
    override fun onPermissionResult(granted: Boolean) {
        if (granted) {
            //Se o usuário aceitar, essa função consegue
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


    //Funções dos botões do menu principal

    //Função para mostrar os diferentes tipos de mapaz
    private fun menuMapa() {
        val itens =
            arrayOf("Trafégo Dia", "Trafégo Noite", "Dark", "Light", "Rua", "Exterior", "Satelite", "Satelite + Rua")
        val builder = AlertDialog.Builder(this@MainActivity)

        with(builder) {
            setTitle("Selecione o tipo de mapa:")
            setItems(itens) { _, which ->
                when (which) {
                    0 -> mapboxMap.setStyle(Style.TRAFFIC_DAY)
                    1 -> mapboxMap.setStyle(Style.TRAFFIC_NIGHT)
                    2 -> mapboxMap.setStyle(Style.DARK)
                    3 -> mapboxMap.setStyle(Style.LIGHT)
                    4 -> mapboxMap.setStyle(Style.MAPBOX_STREETS)
                    5 -> mapboxMap.setStyle(Style.OUTDOORS)
                    6 -> mapboxMap.setStyle(Style.SATELLITE)
                    7 -> mapboxMap.setStyle(Style.SATELLITE_STREETS)
                }
            }
            show()
        }

    }

    private fun menu3D() {
        val fillExtrusionLayer = FillExtrusionLayer("3d-buildings", "composite")
        if (verificaOn3D) {
            verificaOn3D = false
            mapboxMap.removeLayer(fillExtrusionLayer)
            return
        }
        fillExtrusionLayer.sourceLayer = "building"
        fillExtrusionLayer.filter = eq(get("extrude"), "true")
        fillExtrusionLayer.minZoom = 15F
        fillExtrusionLayer.setProperties(
            fillExtrusionColor(Color.LTGRAY),
            fillExtrusionHeight(
                interpolate(
                    exponential(1f),
                    zoom(),
                    stop(15, literal(0)),
                    stop(16, get("height"))
                )
            ),
            fillExtrusionBase(get("min_height")),
            fillExtrusionOpacity(0.9f)
        )
        mapboxMap.addLayer(fillExtrusionLayer)
        verificaOn3D = true
    }

    //Menu para mostrar os diferentes tipos de rota.
    private fun menuRotas() {
        if (PermissionsManager.areLocationPermissionsGranted(this)) {
            val itens =
                arrayOf("Carro", "Bicicleta", "Andar", "Carro (Rodovia)")
            val builder = AlertDialog.Builder(this@MainActivity)

            with(builder) {
                setTitle("Selecione o tipo de mapa:")
                setItems(itens) { _, which ->
                    when (which) {
                        0 -> {
                            meioLocomocao = DirectionsCriteria.PROFILE_DRIVING
                            if (navigationMapRoute != null) {
                                val origemPonto = Point.fromLngLat(origem!!.longitude, origem!!.latitude)
                                val destinoPonto = Point.fromLngLat(destino!!.longitude, destino!!.latitude)
                                procurarRota(origemPonto, destinoPonto)
                            }
                        }
                        1 -> {
                            meioLocomocao = DirectionsCriteria.PROFILE_CYCLING
                            if (navigationMapRoute != null) {
                                val origemPonto = Point.fromLngLat(origem!!.longitude, origem!!.latitude)
                                val destinoPonto = Point.fromLngLat(destino!!.longitude, destino!!.latitude)
                                procurarRota(origemPonto, destinoPonto)
                            }
                        }
                        2 -> {
                            meioLocomocao = DirectionsCriteria.PROFILE_WALKING
                            if (navigationMapRoute != null) {
                                val origemPonto = Point.fromLngLat(origem!!.longitude, origem!!.latitude)
                                val destinoPonto = Point.fromLngLat(destino!!.longitude, destino!!.latitude)
                                procurarRota(origemPonto, destinoPonto)
                            }
                        }
                        3 -> {
                            meioLocomocao = DirectionsCriteria.PROFILE_DRIVING_TRAFFIC
                            if (navigationMapRoute != null) {
                                val origemPonto = Point.fromLngLat(origem!!.longitude, origem!!.latitude)
                                val destinoPonto = Point.fromLngLat(destino!!.longitude, destino!!.latitude)
                                procurarRota(origemPonto, destinoPonto)
                            }
                        }
                    }
                }
                show()
            }

        } else {
            permissionsManager = PermissionsManager(this)
            permissionsManager!!.requestLocationPermissions(this)
        }
    }

    // Função PIP (Somente disponivel para celulares que possuem Android Oreo para cima)
    private fun menuPIP() {
        val builder = AlertDialog.Builder(this@MainActivity)
        builder.setTitle("Atenção!")
        builder.setMessage("Essa função funciona somente com sistemas Android de versão 8.0 para acima, potente o suficiente para executar. Você deseja continuar?")
        builder.setPositiveButton("Sim") { _, _ ->

            if (Build.VERSION.SDK_INT > 25) {

                try {
                    val mPIPParamBuilder = PictureInPictureParams.Builder()
                    this@MainActivity.enterPictureInPictureMode(mPIPParamBuilder.build())
                } catch (exception: Exception) {
                    Toast.makeText(
                        this@MainActivity, R.string.erro_pip,
                        Toast.LENGTH_SHORT
                    ).show()
                }

            } else {
                Toast.makeText(
                    this@MainActivity, R.string.erro_versao_android,
                    Toast.LENGTH_SHORT
                ).show()

            }
        }
        builder.setNegativeButton("Não") { _, _ ->
            //Caso fazer algo ao apertar não
        }
        val dialog: AlertDialog = builder.create()
        dialog.show()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        if (isInPictureInPictureMode) {
            // Hide the full-screen UI (controls, etc.) while in picture-in-picture mode.
            if (locationComponent == null || !locationComponent!!.isLocationComponentEnabled) {
                fabGPSFuncao()
            } else {
                inicializaLocationEngine()
            }

            if (verificaRota!!) {
                cabecalhoRota.visibility = View.GONE
                tabelaRota.visibility = View.GONE
            }

            fabGPS.hide()
            botaoFabMenu.hide()

        } else {
            // Restore the full-screen UI.
            fabGPS.show()
            botaoFabMenu.show()

            if (verificaRota!!) {
                cabecalhoRota.visibility = View.VISIBLE
                tabelaRota.visibility = View.VISIBLE
            }
        }
    }

    //Quando a localização mudar, essa função vai acontecer
    override fun onLocationChanged(location: Location?) {
        if (location != null) {
            origem = location
            moveCameraPosicao(location)
            locationEngine?.removeLocationEngineListener(this@MainActivity)
        }
    }

    //Ao conectar o GPS, essa função vai ser chamada
    @SuppressLint("MissingPermission")
    override fun onConnected() {
        locationEngine?.requestLocationUpdates()
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
        locationEngine?.removeLocationUpdates()
        mapView.onStop()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.onDestroy()

        // Previne vazamento de memória
        navigation?.stopNavigation()
        navigation?.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }

    //Ao pressionar o botão de voltar, essa função acontece
    override fun onBackPressed() {
        if (menuPrincipal.isShowing) {
            menuPrincipal.closeMenu()
            return
        }
        if (verificaRota == true) {
            retiraRota()
        } else {
            val builder = AlertDialog.Builder(this@MainActivity)
            builder.setTitle("Fechar Aplicativo")
            builder.setMessage("Você deseja sair do aplicativo?")
            builder.setPositiveButton("Sim") { _, _ ->
                super.onBackPressed()
            }
            builder.setNegativeButton("Não") { _, _ ->
                //Caso fazer algo ao apertar não
            }
            val dialog: AlertDialog = builder.create()
            dialog.show()
        }
    }

}

