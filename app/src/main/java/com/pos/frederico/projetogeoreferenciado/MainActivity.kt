/* Este projeto foi feito com o intuíto de servir como modelo para desenvolver aplicativos nativos, usando mapas com principal
* ferramenta e o uso do serviço georreferenciado Mapbox, através da linguagem Kotlin. Este trabalho foi feito sendo base para
* um trabalho de conclusão de curso de pós-graduação em Desenvolvimento de Sistemas.
*
* Para quaisquer dúvidas ou sugestões, utilize o link do GitHub: https://github.com/Leminur/Exemplo-Mapbox-Kotlin
* Para dúvidas relacionadas ao uso do gerenciador MapBox, utilize o link (em inglês): https://www.mapbox.com/android-docs/maps/overview/
*
* Espera-se que este projeto ajude os desenvolvedores a utilizar as funções que o gerenciador Mapbox pode oferecer, assim como
* outros desenvolvedores contribuírem com novas funções neste projeto no Github.
*
* Obrigado e bom trabalho,
* - Frederico Fernandes de Faria
* */

/* This project has been developed with the goal to serve as a model to develop native applications, by using Mapbox as the
* main geo-referenced database, and Kotlin as the programming language. This code was done to be the foundation of a post-
* graduation work in Systems Development, alas, most of the code was done in Portuguese, as well with
* the comments. I am sorry about that, but you are free to improve the code and share your comments in English, as I
* am going to translate it to Portuguese.
*
* If you have any doubts or suggestions related to this code, please use this GitHub link: https://github.com/Leminur/Exemplo-Mapbox-Kotlin
* If you have doubts related to the use of the Mapbox tool, please use this link: https://www.mapbox.com/android-docs/maps/overview/
*
* It's hoped that this project helps the developers to use functions that Mapbox offers, as well as any other developer
* contribute to the project with it's newest functions in Github.
*
* Thanks and happy coding,
* - Frederico Fernandes de Faria
* */

package com.pos.frederico.projetogeoreferenciado

import android.annotation.SuppressLint
import android.app.PictureInPictureParams
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v4.content.ContextCompat
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
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
import com.mapbox.mapboxsdk.annotations.IconFactory
import com.mapbox.mapboxsdk.annotations.Marker
import com.mapbox.mapboxsdk.annotations.MarkerOptions
import com.mapbox.mapboxsdk.camera.CameraPosition
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

    //Variáveis globais para serem utilizadas nas funções abaixo
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

        //Uso do token Mapbox. É obrigatório gerar seu token no site mapbox, acesse o arquivo strings.xml para maiores dúvidas.
        Mapbox.getInstance(this, getString(R.string.string_acesso_token))
        navigation = MapboxNavigation(this@MainActivity, getString(R.string.string_acesso_token))

        setContentView(R.layout.activity_main)

        //Chamada e definição do Mapview, elemento da view que gera o Mapa, definido no activity_main.xml
        mapView = findViewById(R.id.mapView)
        mapView.onCreate(savedInstanceState)

        //Valores padrões do mapbox, função de inicialização do mapa
        meioLocomocao = DirectionsCriteria.PROFILE_DRIVING
        mapView.getMapAsync { it ->
            it.setStyle(Style.TRAFFIC_DAY)
            mapboxMap = it

            //Função que é chamada quando o usuário segurar o dedo no mapa, onde neste caso, buscará o menor caminho entre a sua posição
            //e local pressionado.
            it.addOnMapLongClickListener { passaIt: LatLng ->
                if (PermissionsManager.areLocationPermissionsGranted(this)) {
                    destino = passaIt

                    if (locationComponent == null || !locationComponent!!.isLocationComponentEnabled) {
                        fabGPSFuncao()
                        inicializaLocationEngine()
                    }

                    //Variáveis de origem e destino
                    val origemPonto = Point.fromLngLat(origem!!.longitude, origem!!.latitude)
                    val destinoPonto = Point.fromLngLat(destino!!.longitude, destino!!.latitude)

                    //Se existir a origem, procura a rota
                    if (origem != null) {
                        procurarRota(origemPonto, destinoPonto)
                    }

                    //Remove o marcador de destino se já existir
                    marcadorDestino?.let {
                        mapboxMap.removeMarker(it)
                    }

                    //Adiciona o marcador de destino
                    marcadorDestino = mapboxMap.addMarker(
                        MarkerOptions().position(destino)
                    )

                } else {
                    permissionsManager = PermissionsManager(this)
                    permissionsManager!!.requestLocationPermissions(this)
                }
            }

            //Função que exibe a navegação da rota ao procurá-la
            botaoNavegar.setOnClickListener {
                val simulateRoute = false

                val options = NavigationLauncherOptions.builder()
                    .directionsRoute(currentRoute)
                    .shouldSimulateRoute(simulateRoute)
                    .build()
                //Utilizar este metodo usando o context dentro da Activity ativa
                NavigationLauncher.startNavigation(this@MainActivity, options)
            }

            //Função ao clicar o X para retirar a rota encontrada
            cancelaRota.setOnClickListener {
                retiraRota()
            }

            //Adiciona o marcador e atribui funções a ele.
            val iconeFactory: IconFactory = IconFactory.getInstance(this@MainActivity)
            mapboxMap.addMarker(
                MarkerOptions()
                    .position(LatLng(-15.76923, -47.88986))
                    .title("Cathedra")
                    .snippet("Competências Profissionais")
                    .icon(iconeFactory.fromResource(R.drawable.ic_icone_marker_verde))
            )

            //Tratamento da barra de marcadores e adicionando os elementos de marcadores na variavel
            val marcadores: MutableList<Marker> = mapboxMap.markers
            val nomeMarcadores: MutableList<String> = mutableListOf()

            for (marcador in marcadores) {
                nomeMarcadores.add(marcador.title)
            }
            val adaptador: ArrayAdapter<String> = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_dropdown_item_1line, nomeMarcadores
            )

            autoCompleteMarcadores.threshold = 2
            autoCompleteMarcadores.setAdapter(adaptador)

            //Função ao clicar em uma das opções sugeridas na barra de pesquisa
            autoCompleteMarcadores.setOnEditorActionListener { v, actionid, event ->
                var handled = false

                if (event != null && event.action != KeyEvent.ACTION_DOWN) {
                    return@setOnEditorActionListener false

                } else if (actionid == KeyEvent.ACTION_DOWN) {
                    val textoBarra = autoCompleteMarcadores.text.toString()

                    if (nomeMarcadores.contains(textoBarra)) {
                        escondeTeclado(v)
                        autoCompleteMarcadores.setText("")

                        //Pesquisa na lista pelo marcador com o titulo inserido
                        var encontrouMarcador = 0
                        for (marcador in marcadores) {
                            if (marcador.title == textoBarra) {
                                encontrouMarcador = marcadores.indexOf(marcador)
                            }
                        }
                        //Após achar o marcador, faz o mesmo processo de pesquisa com o gps acima
                        val destinoMarcador: Marker = marcadores[encontrouMarcador]
                        val ponto: LatLng = destinoMarcador.position

                        //Atualiza e mostra para o usuario o local indicado
                        val position: CameraPosition = CameraPosition.Builder()
                            .target(ponto)
                            .tilt(0.0)
                            .zoom(17.0)
                            .bearing(0.0)
                            .build()
                        mapboxMap.animateCamera(CameraUpdateFactory.newCameraPosition(position))
                        Toast.makeText(this@MainActivity, "Destino encontrado!", Toast.LENGTH_SHORT).show()

                        //Mostrar a tela automaticamente
                        mapboxMap.selectMarker(destinoMarcador)

                    } else {
                        //Caso o usuário tente pesquisar sem utilizar a opção sugerida, essa função é chamada
                        Toast.makeText(
                            this@MainActivity,
                            "Nenhum marcador foi encontrado com este nome. Utilize as sugestões que foi sugerido!",
                            Toast.LENGTH_LONG
                        ).show()
                    }

                    handled = true
                }
                return@setOnEditorActionListener handled
            }

        }

        //Tratador que atribui valores no menu principal
        try {
            menuPrincipal.setMenu(R.menu.fab_janela)
            menuPrincipal.bindAnchorView(botaoFabMenu)
            menuPrincipal.setOnFABMenuSelectedListener(this@MainActivity)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        //Função ao pressionar o botão de pesquisa de localização.
        fabGPS.setOnClickListener {
            fabGPSFuncao()
        }

    }

    //Opções do menu principal, definidas no aqruivo fab_janela.xml
    override fun onMenuItemSelected(view: View, id: Int) {
        when (id) {
            R.id.menu_Mapas -> menuMapa()
            R.id.menu_3D -> menu3D()
            R.id.menu_Rotas -> menuRotas()
            R.id.menu_PIP -> menuPIP()
        }
    }

    //Função principal para procurar a rota definida pelo usuário
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

                    // Desenha a rota definida no mapa
                    if (navigationMapRoute != null) {
                        navigationMapRoute!!.updateRouteArrowVisibilityTo(false)
                        navigationMapRoute!!.updateRouteVisibilityTo(false)
                    } else {
                        navigationMapRoute = NavigationMapRoute(null, mapView, mapboxMap, R.style.NavigationMapRoute)
                    }
                    navigationMapRoute!!.addRoute(currentRoute)

                    // Transformando dados da distancia em km para exibir na tela
                    val textoDistancia = currentRoute!!.distance()!! / 1000
                    println("Formato do numero: $textoDistancia")
                    val texto2Distancia = String.format("%.2f", textoDistancia) + " KM"
                    distanciaRota.text = texto2Distancia

                    // Transformando dados do tempo no padrão para exibir na tela
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

                    //Mostrar os botões e campos de navegação ao encontrar a rota
                    botaoNavegar.isEnabled = true
                    botaoNavegar.visibility = View.VISIBLE
                    cancelaRota.isEnabled = true
                    cancelaRota.visibility = View.VISIBLE
                    tabelaRota.visibility = View.VISIBLE
                    autoCompleteMarcadores.visibility = View.GONE
                    carregaRota.visibility = View.INVISIBLE

                    movePosicaoRota()

                }

                //Caso não for encontrado uma rota por motivo de conexão, essa função é chamada
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

    // Função de ligar o rastreamento ao clicar no botão
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

    // Habilita o modo GPS, desabilita caso ele já estiver ligado
    @SuppressLint("MissingPermission")
    private fun inicializaGPS() {
        if (locationComponent == null || !locationComponent!!.isLocationComponentEnabled) {

            locationEngine = LocationEngineProvider(this@MainActivity).obtainBestLocationEngineAvailable()
            navigation?.locationEngine = locationEngine!!

            val options: LocationComponentOptions = LocationComponentOptions.builder(this)
                .trackingGesturesManagement(true)
                .accuracyColor(ContextCompat.getColor(this, R.color.padrao_azul_menu_200))
                .build()

            // Define uma instancia do componente
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

    // Inicializa a ferramenta de localização
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

    // Move a posição da camera até a posição enviada
    private fun moveCameraPosicao(location: Location) {
        mapboxMap.animateCamera(
            CameraUpdateFactory.newLatLngZoom(
                LatLng(location.latitude, location.longitude),
                17.0
            )
        )
    }

    // Quando encontrado uma rota, mostrar na tela a origem e destino
    private fun movePosicaoRota() {
        val origemLatLng = LatLng(origem!!.latitude, origem!!.longitude)

        val latLngBounds: LatLngBounds = LatLngBounds.Builder()
            .include(destino!!)
            .include(origemLatLng)
            .build()

        mapboxMap.easeCamera(CameraUpdateFactory.newLatLngBounds(latLngBounds, 300), 1000)
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
        autoCompleteMarcadores.visibility = View.VISIBLE

        marcadorDestino?.let {
            mapboxMap.removeMarker(it)
        }

        verificaRota = false
    }

    //Funções que gerenciam a requisição das permissões de uso do GPS
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        permissionsManager?.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun onExplanationNeeded(permissionsToExplain: MutableList<String>?) {
        //Inserir aqui dados para avisar o usuário antes da decisão de aceitar ou não ser feita
    }

    //Função que gerencia o que acontece quando o usuário dá permissões
    override fun onPermissionResult(granted: Boolean) {
        if (granted) {
            //Se o usuário aceitar, essa função consegue
            fabGPSFuncao()
        } else {
            //Caso o usuário recusar as permissões, a função abaixo será ser executada
            val snackbar = Snackbar.make(root_layout, R.string.permissao_explicacao, Snackbar.LENGTH_INDEFINITE)
            snackbar.setAction("Fechar") {
                snackbar.dismiss()
            }
            snackbar.show()
        }
    }

    //Função para mostrar os diferentes tipos de mapas
    private fun menuMapa() {
        val itens =
            arrayOf("Trânsito Dia", "Trânsito Noite", "Dark", "Light", "Rua", "Exterior", "Satelite", "Satelite + Rua")
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

    // Função para ligar ou desligar o modo 3D
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

    //Menu para utilizar diferentes tipos de transporte na rota.
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

    // Função para esconder o teclado quando estiver sendo exibido
    private fun escondeTeclado(view: View) {
        val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    // Função para habilitar o modo PIP (Somente disponivel para celulares que possuem Android Oreo para cima)
    private fun menuPIP() {
        val builder = AlertDialog.Builder(this@MainActivity)
        builder.setTitle("Atenção!")
        builder.setMessage("Essa função funciona somente com sistemas Android de versão 8.0 para acima, potente o suficiente para executar. Você deseja continuar?")
        builder.setPositiveButton("Sim") { _, _ ->

            if (Build.VERSION.SDK_INT > 25) {

                if (locationComponent == null || !locationComponent!!.isLocationComponentEnabled) {
                    fabGPSFuncao()
                } else {
                    inicializaLocationEngine()
                }

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
            //Disparar algo quando o usuãrio apertar não
        }
        val dialog: AlertDialog = builder.create()
        dialog.show()
    }

    //Função que executa diferentes comandos se o modo PIP estiver habilitado, neste caso, ele esconde os botões para não atrapalhar a navegação PIP
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        if (isInPictureInPictureMode) {

            // Esconde os controladores de interface de usuário quando o modo PIP estiver ligado
            if (verificaRota != null && verificaRota!!) {
                cabecalhoRota.visibility = View.GONE
                tabelaRota.visibility = View.GONE
            }

            autoCompleteMarcadores.visibility = View.GONE
            fabGPS.hide()
            botaoFabMenu.hide()

        } else {
            // Restora os controladores de interface.
            fabGPS.show()
            botaoFabMenu.show()

            if (verificaRota != null && verificaRota!!) {
                cabecalhoRota.visibility = View.VISIBLE
                tabelaRota.visibility = View.VISIBLE
            } else {
                autoCompleteMarcadores.visibility = View.VISIBLE
            }
        }
    }

    //Quando a localização mudar, essa função será chamada
    override fun onLocationChanged(location: Location?) {
        if (location != null) {
            origem = location
            moveCameraPosicao(location)
            locationEngine?.removeLocationEngineListener(this@MainActivity)
        }
    }

    //Ao conectar o GPS, essa função será chamada
    @SuppressLint("MissingPermission")
    override fun onConnected() {
        locationEngine?.requestLocationUpdates()
    }


    // Funções de status padrão do app, usando o mapview
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

    //Ao pressionar o botão de voltar no celular, essa função acontece
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

