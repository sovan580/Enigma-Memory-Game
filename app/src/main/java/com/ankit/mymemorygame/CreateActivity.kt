package com.ankit.mymemorygame

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ankit.mymemorygame.models.BoardSize
import com.ankit.mymemorygame.utils.*
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import java.io.ByteArrayOutputStream

class CreateActivity : AppCompatActivity() {
    companion object{
        private const val TAG="CreateActivity"
        private const val PICK_PHOTO_CODE=655
        private const val READ_EXTERNAL_PHOTOS_CODE=248
        private const val READ_PHOTOS_PERMISSION=android.Manifest.permission.READ_EXTERNAL_STORAGE
        private const val MIN_GAME_NAME_LENGTH=3
        private const val MAX_GAME_NAME_LENGTH=14
    }
    private lateinit var pbUploading:ProgressBar
    private lateinit var rvImagePicker: RecyclerView
    private lateinit var etGameName: EditText
    private lateinit var btnSave: Button
    private lateinit var boardSize: BoardSize
    private var numImagesRequired=-1
    private lateinit var adapter: ImagePickerAdapter
    private val chosenImagesUris= mutableListOf<Uri>()
    private val storage=Firebase.storage
    private val db=Firebase.firestore
    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create)
        rvImagePicker=findViewById(R.id.rvImagePicker)
        etGameName=findViewById(R.id.edGameName)
        btnSave=findViewById(R.id.btn_saved)
        pbUploading=findViewById(R.id.pbUploading)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        boardSize=intent.getSerializableExtra(EXTRA_BOARD_SIZE) as BoardSize
        numImagesRequired=boardSize.getNumPairs()
        supportActionBar?.title="Choose Pictures (0/$numImagesRequired)"
        btnSave.setOnClickListener{
            saveDatatoFirebase()
        }
        etGameName.filters= arrayOf(InputFilter.LengthFilter(MAX_GAME_NAME_LENGTH))
        etGameName.addTextChangedListener(object: TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?){
                btnSave.isEnabled=shouldEnableSaveButton()
            }

        })
        adapter=ImagePickerAdapter(this,chosenImagesUris,boardSize,object :ImagePickerAdapter.ImageClickListener{
            override fun onPlaceholderClicked() {
                if(isPermissionGranted(this@CreateActivity, READ_PHOTOS_PERMISSION)){
                    launchIntentForPhotos()
                }
                else{
                    requestPermission(this@CreateActivity, READ_PHOTOS_PERMISSION, READ_EXTERNAL_PHOTOS_CODE)
                }
            }


        })
        rvImagePicker.adapter=adapter
        rvImagePicker.setHasFixedSize(true)
        rvImagePicker.layoutManager= GridLayoutManager(this,boardSize.getWidth())
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if(requestCode== READ_EXTERNAL_PHOTOS_CODE){
            if(grantResults.isNotEmpty() && grantResults[0]== PackageManager.PERMISSION_GRANTED){
                launchIntentForPhotos()
            }
            else{
                Toast.makeText(this,"In order to create a custom game, you need to provide access to your photos", Toast.LENGTH_LONG).show()
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if(item.itemId==android.R.id.home){
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != PICK_PHOTO_CODE || resultCode != Activity.RESULT_OK || data == null) {
            Log.w(TAG, "Did not get data back from the launched activity,user likely canceled flow")
            return
        }
        val selectUri = data.data
        val clipData = data.clipData
        if (clipData != null) {
            Log.i(TAG, "clipData numImages ${clipData.itemCount}: $clipData")
            for(i in 0 until clipData.itemCount){
                val clipItem=clipData.getItemAt(i)
                if(chosenImagesUris.size<numImagesRequired){
                    chosenImagesUris.add(clipItem.uri)
                }
            }
        }
        else if(selectUri!=null){
            Log.i(TAG,"data:$selectUri")
            chosenImagesUris.add(selectUri)
        }
        adapter.notifyDataSetChanged()
        supportActionBar?.title="Choose pictures (${chosenImagesUris.size}/$numImagesRequired"
        btnSave.isEnabled=shouldEnableSaveButton()
    }

    private fun shouldEnableSaveButton(): Boolean {
        if(chosenImagesUris.size!=numImagesRequired){
            return false
        }
        if(etGameName.text.isBlank()||etGameName.text.length< MIN_GAME_NAME_LENGTH){
            return false
        }
        return true
    }

    private fun launchIntentForPhotos() {
        val intent= Intent(Intent.ACTION_PICK)
        intent.type="image/*"
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true)
        startActivityForResult(Intent.createChooser(intent,"Choose Pictures"),PICK_PHOTO_CODE)
    }
    private fun saveDatatoFirebase() {
        btnSave.isEnabled=false
        val customGameName=etGameName.text.toString()
        db.collection("games").document(customGameName).get().addOnSuccessListener { document->
            if(document!=null && document.data!=null){
                AlertDialog.Builder(this).setTitle("Name Taken")
                    .setMessage("A game already exist with the name '$customGameName'. Please choose another game name.")
                    .setPositiveButton("OK",null).show()
                btnSave.isEnabled=true
            }
            else{
                handleImageUploading(customGameName)
            }
        }.addOnFailureListener{exception->
            Toast.makeText(this,"Encountered error while saving the game",Toast.LENGTH_SHORT).show()
            btnSave.isEnabled=true
        }

    }

    private fun handleImageUploading(gameName: String) {
        pbUploading.visibility= View.VISIBLE
        var didEncounterError=false
        val uploadImageUrls= mutableListOf<String>()
        for((index,photoUri) in chosenImagesUris.withIndex()){
            val imageByteArray=getImageByteArray(photoUri)
            val filePath="images/$gameName/${System.currentTimeMillis()}-${index}.jpg"
            val photoReference=storage.reference.child(filePath)
            photoReference.putBytes(imageByteArray).continueWithTask{photoUploadTask->
                photoReference.downloadUrl
            }.addOnCompleteListener{downloadUrlTask->
                if(!downloadUrlTask.isSuccessful){
                    Toast.makeText(this,"Failed to Upload Image", Toast.LENGTH_SHORT).show()
                    didEncounterError=true
                    return@addOnCompleteListener
                }
                if(didEncounterError){
                    pbUploading.visibility=View.GONE
                    return@addOnCompleteListener
                }

                val downloadUrl=downloadUrlTask.result.toString()
                uploadImageUrls.add(downloadUrl)
                pbUploading.progress=uploadImageUrls.size*100/chosenImagesUris.size
                if(uploadImageUrls.size==chosenImagesUris.size){
                    handleAllImagesUploaded(gameName,uploadImageUrls)
                }
            }
        }
    }

    private fun handleAllImagesUploaded(gameName: String, imageUrls: MutableList<String>) {
        db.collection("games").document(gameName).set(mapOf("images" to imageUrls))
                .addOnCompleteListener{gameCreationTask->
                    pbUploading.visibility=View.GONE
                    if(!gameCreationTask.isSuccessful){
                        Toast.makeText(this,"Failed Game Creation", Toast.LENGTH_SHORT).show()
                        return@addOnCompleteListener
                    }
                    AlertDialog.Builder(this).setTitle("Upload complete! Let's play your '$gameName' Game")
                            .setPositiveButton("OK"){_,_->
                                val resultData= Intent()
                                resultData.putExtra(EXTRA_GAME_NAME,gameName)
                                setResult(Activity.RESULT_OK,resultData)
                                finish()
                            }.show()
                }

    }

    private fun getImageByteArray(photoUri: Uri): ByteArray {
        val originalBitmap=if(Build.VERSION.SDK_INT>= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(contentResolver, photoUri)
            ImageDecoder.decodeBitmap(source)
        }
        else {
            MediaStore.Images.Media.getBitmap(contentResolver, photoUri)
        }
        val scaledBitmap= BitmapScaler.scaleToFitHeight(originalBitmap,250)
        val byteOutputStream= ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG,60,byteOutputStream)
        return byteOutputStream.toByteArray()
    }
}