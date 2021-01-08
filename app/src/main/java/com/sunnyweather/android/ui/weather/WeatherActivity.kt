package com.sunnyweather.android.ui.weather


import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.location.AMapLocationListener
import com.sunnyweather.android.R
import com.sunnyweather.android.logic.model.RealtimeResponse
import com.sunnyweather.android.logic.model.Weather
import com.sunnyweather.android.logic.model.getSky
import kotlinx.android.synthetic.main.activity_weather.*
import kotlinx.android.synthetic.main.forecast.*
import kotlinx.android.synthetic.main.forecast_item.*
import kotlinx.android.synthetic.main.life_index.*
import kotlinx.android.synthetic.main.now.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class WeatherActivity : AppCompatActivity() {

    val viewModel by lazy { ViewModelProviders.of(this).get(WeatherViewModel::class.java) }
//    val viewModel1 by lazy { ViewModelProviders.of(this).get() }

    override fun onCreate(savedInstanceState: Bundle? ) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 21) {
            val decorView = window.decorView
            decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            window.statusBarColor = Color.TRANSPARENT
        }
        setContentView(R.layout.activity_weather)
        if (viewModel.locationLng.isEmpty()) {
            viewModel.locationLng = intent.getStringExtra("location_lng") ?: ""
        }
        if (viewModel.locationLat.isEmpty()) {
            viewModel.locationLat = intent.getStringExtra("location_lat") ?: ""
        }
        if (viewModel.placeName.isEmpty()) {
            viewModel.placeName = intent.getStringExtra("place_name") ?: ""
        }
        viewModel.weatherLiveData.observe(this, Observer { result ->
            val weather = result.getOrNull()
            if (weather != null) {
                showWeatherInfo(weather)
            } else {
                Toast.makeText(this, "无法成功获取天气信息", Toast.LENGTH_SHORT).show()
                result.exceptionOrNull()?.printStackTrace()
            }
            swipeRefresh.isRefreshing = false
        })
        swipeRefresh.setColorSchemeResources(R.color.colorPrimary)
        refreshWeather()
        swipeRefresh.setOnRefreshListener {
            refreshWeather()
        }
        navBtn.setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }
        location.setOnClickListener {
            initMap()
            Log.d("WeatherActivity","点击定位")
//            refreshWeather()


        }
        drawerLayout.addDrawerListener(object : DrawerLayout.DrawerListener {
            override fun onDrawerStateChanged(newState: Int) {}

            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {}

            override fun onDrawerOpened(drawerView: View) {}

            override fun onDrawerClosed(drawerView: View) {
                val manager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                manager.hideSoftInputFromWindow(drawerView.windowToken, InputMethodManager.HIDE_NOT_ALWAYS)
            }
        })
    }

    fun refreshWeather() {
        viewModel.refreshWeather(viewModel.locationLng, viewModel.locationLat)
        swipeRefresh.isRefreshing = true
    }

    private fun showWeatherInfo(weather: Weather) {
        placeName.text = viewModel.placeName
        val realtime = weather.realtime
        val daily = weather.daily
        // 填充now.xml布局中数据
        val currentTempText = "${realtime.temperature.toInt()} ℃"
        currentTemp.text = currentTempText
        currentSky.text = getSky(realtime.skycon).info
        val currentPM25Text = "空气指数 ${realtime.airQuality.aqi.chn.toInt()}"
        currentAQI.text = currentPM25Text
        nowLayout.setBackgroundResource(getSky(realtime.skycon).bg)
        // 填充forecast.xml布局中的数据
        forecastLayout.removeAllViews()
        val days = daily.skycon.size
        for (i in 0 until days) {
            val skycon = daily.skycon[i]
            val temperature = daily.temperature[i]
            val view = LayoutInflater.from(this).inflate(R.layout.forecast_item, forecastLayout, false)
            val dateInfo = view.findViewById(R.id.dateInfo) as TextView
            val skyIcon = view.findViewById(R.id.skyIcon) as ImageView
            val skyInfo = view.findViewById(R.id.skyInfo) as TextView
            val temperatureInfo = view.findViewById(R.id.temperatureInfo) as TextView
            val simpleDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            dateInfo.text = simpleDateFormat.format(skycon.date)
            val sky = getSky(skycon.value)
            skyIcon.setImageResource(sky.icon)
            skyInfo.text = sky.info
            val tempText = "${temperature.min.toInt()} ~ ${temperature.max.toInt()} ℃"
            temperatureInfo.text = tempText
            forecastLayout.addView(view)
        }
        fenxiang.setOnClickListener {
            var shareIntent = Intent()
            shareIntent.action = Intent.ACTION_SEND
            shareIntent.type = "text/plain"
            shareIntent.putExtra("android.intent.extra.SUBJECT", "分享")
            shareIntent.putExtra(Intent.EXTRA_TEXT, "${viewModel.placeName}的温度 ："+"${realtime.temperature.toInt()} ℃" )

//            " $dateInfo"+ " $skyIcon" + " $skyInfo" + " $temperatureInfo"
            shareIntent = Intent.createChooser(shareIntent, "分享")
            startActivity(shareIntent)
        }

        // 填充life_index.xml布局中的数据
        val lifeIndex = daily.lifeIndex
        coldRiskText.text = lifeIndex.coldRisk[0].desc
        dressingText.text = lifeIndex.dressing[0].desc
        ultravioletText.text = lifeIndex.ultraviolet[0].desc
        carWashingText.text = lifeIndex.carWashing[0].desc
        weatherLayout.visibility = View.VISIBLE
    }
    var mLocationClient: AMapLocationClient? = null
    //声明定位回调监听器
    var mLocationOption: AMapLocationClientOption? = null
    private fun initMap() {

        //初始化定位
        mLocationClient = AMapLocationClient(this)
        //设置定位回调监听
        mLocationClient!!.setLocationListener(mLocationListener)
        mLocationOption = AMapLocationClientOption()
        //设置定位模式为高精度模式，AMapLocationMode.Battery_Saving为低功耗模式，AMapLocationMode.Device_Sensors是仅设备模式
        mLocationOption!!.locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
        mLocationOption!!.isNeedAddress = true //设置是否返回地址信息（默认返回地址信息）
        mLocationOption!!.isOnceLocation = false //设置是否只定位一次,默认为false
        mLocationOption!!.isWifiActiveScan = true //设置是否强制刷新WIFI，默认为强制刷新
        mLocationOption!!.isMockEnable = false //设置是否允许模拟位置,默认为false，不允许模拟位置
        mLocationOption!!.interval = 15000 //设置定位间隔,单位毫秒,默认为2000ms
        mLocationOption!!.isOnceLocation = false //可选，是否设置单次定位默认为false即持续定位
        mLocationOption!!.isOnceLocationLatest =
                false //可选，设置是否等待wifi刷新，默认为false.如果设置为true,会自动变为单次定位，持续定位时不要使用
        mLocationOption!!.isWifiScan =
                true //可选，设置是否开启wifi扫描。默认为true，如果设置为false会同时停止主动刷新，停止以后完全依赖于系统刷新，定位位置可能存在误差
        mLocationOption!!.isLocationCacheEnable = true //可选，设置是否使用缓存定位，默认为true
        //给定位客户端对象设置定位参数
        mLocationClient!!.setLocationOption(mLocationOption)
        //启动定位
        mLocationClient!!.startLocation()

    }
         private var mLocationListener =
                AMapLocationListener { aMapLocation ->
                    if (aMapLocation != null) {
                        Log.d("WeatherActivity","viewModel1.locationLng")
                        if (aMapLocation.errorCode == 0) {
                            Log.d("WeatherActivity","viewModel1.locationLng")
                            //定位成功回调信息，设置相关消息
                            aMapLocation.locationType //获取当前定位结果来源，如网络定位结果，详见定位类型表
                            // aMapLocation.getLatitude();//获取纬度
                            // aMapLocation.getLongitude();//获取经度
                            aMapLocation.accuracy //获取精度信息
                            //val df = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

                                viewModel.locationLng = aMapLocation.longitude.toString()
                                Log.d("WeatherActivity",viewModel.locationLng)
                                viewModel.locationLat = aMapLocation.latitude.toString()
                                Log.d("WeatherActivity",viewModel.locationLat)

                            //  aMapLocation.getAddress();//地址，如果option中设置isNeedAddress为false，则没有此结果，网络定位结果中会有地址信息，GPS定位不返回地址信息。
                            //  aMapLocation.getCountry();//国家信息
                            //  aMapLocation.getProvince();//省信息
                            //  aMapLocation.getCity();//城市信息
                            //   aMapLocation.getDistrict();//城区信息
                            //    aMapLocation.getStreet();//街道信息
                            //     aMapLocation.getStreetNum();//街道门牌号信息
                            //    aMapLocation.getCityCode();//城市编码
                            //     aMapLocation.getAdCode();//地区编码
                            println("所在城市：" + aMapLocation.country + aMapLocation.province + aMapLocation.city)
                            viewModel.placeName = aMapLocation.city + aMapLocation.district + aMapLocation.street
                            placeName.text = viewModel.placeName
                            viewModel.refreshWeather(aMapLocation.longitude.toString(),aMapLocation.latitude.toString())
                            mLocationClient!!.stopLocation() //停止定位
                            Log.d("WeatherActivity",aMapLocation.province)
                        } else {

                            //显示错误信息ErrCode是错误码，errInfo是错误信息，详见错误码表。
                            Log.e(
                                    "info", "location Error, ErrCode:"
                                    + aMapLocation.errorCode + ", errInfo:"
                                    + aMapLocation.errorInfo
                            )
                        }
                    }
                }





//    private fun refreshWeather1() {
//        viewModel1.refreshWeather(viewModel1.locationLng, viewModel1.locationLat)
//        swipeRefresh.isRefreshing = true
//    }
    override fun onDestroy() {
        super.onDestroy()
        //销毁
        if (mLocationClient != null) {
            mLocationClient!!.onDestroy()
        }
    }
}
