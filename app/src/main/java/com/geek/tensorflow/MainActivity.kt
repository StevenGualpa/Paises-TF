package com.geek.tensorflow

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.ThumbnailUtils
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.geek.tensorflow.ml.Model
import org.json.JSONArray
import org.json.JSONObject
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

class MainActivity : AppCompatActivity() {

    var imageSize = 224
    lateinit var ImgCaptura : ImageView
    lateinit var resultado : TextView
    lateinit var confianza : TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        ImgCaptura = findViewById<ImageView>(R.id.ImgCamara)
        resultado=findViewById(R.id.txtresultado)
        confianza=findViewById(R.id.txtcoincidencia)

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == 1 && resultCode == RESULT_OK) {
            var image = data!!.extras!!["data"] as Bitmap?
            val dimension = Math.min(image!!.width, image.height)
            image = ThumbnailUtils.extractThumbnail(image, dimension, dimension)
            ImgCaptura.setImageBitmap(image)
            image = Bitmap.createScaledBitmap(image, imageSize, imageSize, false)
            ClasificarImagen(image)
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    fun CapturaFoto(view: View?) {
        // Iniciar cámara si tenemos permiso
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            startActivityForResult(cameraIntent, 1)
        } else {
            //Solicitar permiso de cámara si no lo tenemos.
            requestPermissions(arrayOf(Manifest.permission.CAMERA), 100)
        }
    }
    fun MensajeLargo(Mensaje: String) {
        Toast.makeText(this, Mensaje.toString(), Toast.LENGTH_LONG).show()

    }

    fun ClasificarImagen(image: Bitmap) {
        try {
            val model = Model.newInstance(applicationContext)

            // Creates inputs for reference.
            val inputFeature0 =
                TensorBuffer.createFixedSize(intArrayOf(1, 224, 224, 3), DataType.FLOAT32)
            val byteBuffer = ByteBuffer.allocateDirect(4 * imageSize * imageSize * 3)
            byteBuffer.order(ByteOrder.nativeOrder())

            // get 1D array of 224 * 224 pixels in image
            val intValues = IntArray(imageSize * imageSize)
            image.getPixels(intValues, 0, image.width, 0, 0, image.width, image.height)

            // iterate over pixels and extract R, G, and B values. Add to bytebuffer.
            var pixel = 0
            for (i in 0 until imageSize) {
                for (j in 0 until imageSize) {
                    val `val` = intValues[pixel++] // RGB
                    byteBuffer.putFloat((`val` shr 16 and 0xFF) * (1f / 255f))
                    byteBuffer.putFloat((`val` shr 8 and 0xFF) * (1f / 255f))
                    byteBuffer.putFloat((`val` and 0xFF) * (1f / 255f))
                }
            }
            inputFeature0.loadBuffer(byteBuffer)

            // Runs model inference and gets result.
            val outputs = model.process(inputFeature0)
            val outputFeature0 = outputs.outputFeature0AsTensorBuffer
            val confidences = outputFeature0.floatArray


            val classes = arrayOf("Agrarias", "Agropecuaria", "Biblioteca", "Enfermerias","FCI","Laboratorio")

            // find the index of the class with the biggest confidence.
            ExtraeDatos(confidences,classes)

            // Releases model resources if no longer used.
            model.close()
        } catch (e: IOException) {
            // TODO Handle the exception
        }
    }

    fun ExtraeDatos(confidences: FloatArray, classes: Array<String>)
    {
        var maxPos = 0
        var maxConfidence = 0f

        for (i in confidences.indices) {
            if (confidences[i] > maxConfidence) {
                maxConfidence = confidences[i]
                maxPos = i
            }
        }


        resultado.setText(classes[maxPos])
        var s = ""
        for (i in classes.indices) {
            s += String.format("%s: %.1f%%\n", classes[i], confidences[i] * 100)
        }
        //MensajeLargo(s)
        confianza.setText(s)
        notification("Excelente",classes[maxPos])
        DescargarMarcadores(classes[maxPos])
    }

    fun notification(titu: String,descrp:String) {

        val chanelid="chat"
        val chanelname="chat"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        {
            val importancia= NotificationManager.IMPORTANCE_HIGH
            val channel= NotificationChannel(chanelid, chanelname,importancia)

            //Manager de Notificacion
            val manager=getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)

            val notification= NotificationCompat.Builder(this, chanelid).also { noti->
                noti.setContentTitle(titu)
                noti.setContentText(descrp)
                noti.setSmallIcon(R.drawable.iconots)
            }.build()

            val NotificationManager= NotificationManagerCompat.from(applicationContext)
            NotificationManager.notify(1, notification)
        }

    }

    fun DescargarMarcadores(parm1: String)
    {
        val queue = Volley.newRequestQueue(this)
        val url: String = "https://my-json-server.typicode.com/StevenGualpa/Api_Uteq_MarketPlace/Edificios"

        // Request a string response from the provided URL.
        val stringReq = StringRequest(
            Request.Method.GET, url,
            { response ->
                var strResp = response.toString()
                var str: JSONArray = JSONArray(strResp)

                //Contador
                var index=0
                //Cantidad de Elementos
                var n=str.length()
                //Variables auxiliares  que usaremos

                //MensajeLargo(n.toString())
                while (index<n) {
                    var elemento: JSONObject = str.getJSONObject(index)

                    if(elemento.getString("Titulo").contains(parm1))
                    {
                        var parametros: String=""
                        parametros=resultado.text.toString() +"\n"+ elemento.getString("Ubicacion")+"\n"+ elemento.getString("Latitude")+";"+ elemento.getString("Longitude")
                        resultado.text=parametros
                        index=1000
                    }

                    index++

                }
            },
            { Log.d("API", "that didn't work") })
        queue.add(stringReq)
    }
}