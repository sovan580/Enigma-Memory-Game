package com.ankit.mymemorygame

import android.app.Activity
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import com.ankit.mymemorygame.models.BoardSize
import com.ankit.mymemorygame.utils.EXTRA_BOARD_SIZE
import com.ankit.mymemorygame.utils.EXTRA_GAME_NAME
import me.ibrahimsn.particle.ParticleView

class GameMenu : Activity() {
    companion object{
        const val CREATE_REQUEST_CODE=247
    }
    private lateinit var playGame:Button
    private lateinit var createGame:Button
    private lateinit var downloadGame:Button
    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game_menu)
        playGame=findViewById(R.id.play_game)
        createGame=findViewById(R.id.create_game)
        downloadGame=findViewById(R.id.download_game)
        playGame.setOnClickListener {
            intent= Intent(this,MainActivity::class.java)
            startActivity(intent)
        }
        createGame.setOnClickListener {
             showCreationDialog()
        }
        downloadGame.setOnClickListener {
            showDownloadDialog()
        }
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if(requestCode == CREATE_REQUEST_CODE && resultCode== Activity.RESULT_OK){
            val customGameName=data?.getStringExtra(EXTRA_GAME_NAME)
            if(customGameName==null){
                return
            }
            val intent=Intent(this,MainActivity::class.java)
            intent.putExtra(EXTRA_GAME_NAME,customGameName)
            startActivityForResult(intent, CREATE_REQUEST_CODE)
        }
        super.onActivityResult(requestCode, resultCode, data)
    }
    private fun showDownloadDialog() {
        val boardDownloadView=LayoutInflater.from(this).inflate(R.layout.dialog_download_board,null)
        showAlertDialog("Fetch memory game",boardDownloadView,View.OnClickListener {
            val etDownloadGame=boardDownloadView.findViewById<EditText>(R.id.edDownloadGame)
            val gameToDownload=etDownloadGame.text.toString().trim()
            val intent=Intent(this,MainActivity::class.java)
            intent.putExtra(EXTRA_GAME_NAME,gameToDownload)
            startActivity(intent)
        })
    }
    private fun showCreationDialog() {
        val boardSizeView= LayoutInflater.from(this).inflate(R.layout.dialog_board_size,null)
        val radioGroupSize=boardSizeView.findViewById<RadioGroup>(R.id.radioGroup)
        showAlertDialog("Create Your Memory Board",boardSizeView, View.OnClickListener {
            val desiredBoardSize=when(radioGroupSize.checkedRadioButtonId){
                R.id.rbEasy-> BoardSize.EASY
                R.id.rbMedium-> BoardSize.MEDIUM
                else-> BoardSize.HARD
            }
            val intent= Intent(this,CreateActivity::class.java)
            intent.putExtra(EXTRA_BOARD_SIZE,desiredBoardSize)
            startActivityForResult(intent, CREATE_REQUEST_CODE)
        })
    }
    private fun showAlertDialog(title:String, view: View?, positiveClickListener: View.OnClickListener) {
        AlertDialog.Builder(this).setTitle(title).setView(view).setNegativeButton("Cancel",null).setPositiveButton("OK"){ _, _->
            positiveClickListener.onClick(null)
        }.show()
    }

}