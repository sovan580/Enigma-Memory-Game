package com.ankit.mymemorygame

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ankit.mymemorygame.models.BoardSize
import com.ankit.mymemorygame.models.MemoryGame
import com.ankit.mymemorygame.models.UserImageList
import com.ankit.mymemorygame.utils.EXTRA_BOARD_SIZE
import com.ankit.mymemorygame.utils.EXTRA_GAME_NAME
import com.github.jinatonic.confetti.CommonConfetti
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.squareup.picasso.Picasso

class MainActivity : AppCompatActivity() {
    companion object{
        private const val TAG="MainActivity"
        const val CREATE_REQUEST_CODE=248
    }
    private lateinit var clRoot: CoordinatorLayout
    private lateinit var  game_board: RecyclerView
    private lateinit var moves: TextView
    private var customGameImages:List<String>?=null
    private lateinit var pairs: TextView
    private lateinit var memoryGame: MemoryGame
    private lateinit var adapter:MemoryBoardAdapter
    private var boardSize: BoardSize = BoardSize.EASY
    private val db=Firebase.firestore
    private var gameName:String?=null
    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        clRoot=findViewById(R.id.clRoot)
        game_board=findViewById(R.id.game_board)
        moves=findViewById(R.id.moves)
        pairs=findViewById(R.id.pairs)
        if(intent.hasExtra(EXTRA_GAME_NAME)){
            gameName=intent.getSerializableExtra(EXTRA_GAME_NAME) as String
        }
        if(!gameName.isNullOrEmpty()){
            downloadGame(gameName!!)
        }
        else{
            setupBoard()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main,menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when(item.itemId){
            R.id.refresh->{
                if(memoryGame.getNumMoves()>0 && !memoryGame.haveWonGame()){
                    showAlertDialog("Quit your current Game ?",null, View.OnClickListener {
                        setupBoard()
                    })
                }else {
                    setupBoard()
                }
            }
            R.id.new_size->{
                showNewSizedDialog()
                return true
            }
            R.id.custom->{
                showCreationDialog()
                return true
            }
            R.id.download_game->{
                showDownloadDialog()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }



    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if(requestCode== CREATE_REQUEST_CODE && resultCode== Activity.RESULT_OK){
            val customGameName=data?.getStringExtra(EXTRA_GAME_NAME)
            if(customGameName==null){
                return
            }
            downloadGame(customGameName)
        }
        super.onActivityResult(requestCode, resultCode, data)
    }
    private fun showDownloadDialog() {
        val boardDownloadView=LayoutInflater.from(this).inflate(R.layout.dialog_download_board,null)
        showAlertDialog("Fetch memory game",boardDownloadView,View.OnClickListener {
            val etDownloadGame=boardDownloadView.findViewById<EditText>(R.id.edDownloadGame)
             val gameToDownload=etDownloadGame.text.toString().trim()
            downloadGame(gameToDownload)
        })
    }

    private fun downloadGame(customGameName: String) {
         db.collection("games").document(customGameName).get().addOnSuccessListener { document->
             val userImageList:UserImageList?=document.toObject(UserImageList::class.java)
             if(userImageList?.images==null){
                 Snackbar.make(clRoot,"Sorry, we couldn't find any such game., '$gameName",Snackbar.LENGTH_LONG).show()
                 return@addOnSuccessListener
             }
             val numCards=userImageList.images.size*2
             boardSize= BoardSize.getByValue(numCards)
             customGameImages=userImageList.images
             gameName=customGameName
             for(imageUrl in userImageList.images){
                 Picasso.get().load(imageUrl).fetch()
             }
             Snackbar.make(clRoot,"You're now playing '$customGameName'!",Snackbar.LENGTH_LONG).show()

             setupBoard()
         }.addOnFailureListener{exception->

         }


    }

    private fun showCreationDialog() {
        val boardSizeView= LayoutInflater.from(this).inflate(R.layout.dialog_board_size,null)
        val radioGroupSize=boardSizeView.findViewById<RadioGroup>(R.id.radioGroup)
        showAlertDialog("Create Your Memory Board",boardSizeView, View.OnClickListener {
            val desiredBoardSize=when(radioGroupSize.checkedRadioButtonId){
                R.id.rbEasy->BoardSize.EASY
                R.id.rbMedium->BoardSize.MEDIUM
                else->BoardSize.HARD
            }
            val intent= Intent(this,CreateActivity::class.java)
            intent.putExtra(EXTRA_BOARD_SIZE,desiredBoardSize)
            startActivityForResult(intent,CREATE_REQUEST_CODE)
        })
    }

    private fun showNewSizedDialog() {
        val boardSizeView= LayoutInflater.from(this).inflate(R.layout.dialog_board_size,null)
        val radioGroupSize=boardSizeView.findViewById<RadioGroup>(R.id.radioGroup)
        when(boardSize){
            BoardSize.EASY -> radioGroupSize.check(R.id.rbEasy)
            BoardSize.MEDIUM -> radioGroupSize.check(R.id.rbMedium)
            BoardSize.HARD -> radioGroupSize.check(R.id.rbHard)
        }
        showAlertDialog("Choose new size",boardSizeView, View.OnClickListener {
            boardSize=when(radioGroupSize.checkedRadioButtonId){
                R.id.rbEasy->BoardSize.EASY
                R.id.rbMedium->BoardSize.MEDIUM
                else->BoardSize.HARD
            }
            gameName=null
            customGameImages=null
            setupBoard()
        })
    }

    private fun showAlertDialog(title:String, view: View?, positiveClickListener: View.OnClickListener) {
        AlertDialog.Builder(this).setTitle(title).setView(view).setNegativeButton("Cancel",null).setPositiveButton("OK"){ _, _->
            positiveClickListener.onClick(null)
        }.show()
    }

    private fun setupBoard() {
        supportActionBar?.title=gameName?:getString(R.string.app_name)
        when(boardSize){
            BoardSize.EASY->{
                moves.text="Easy : 4 x 2"
                pairs.text="Pairs : 0 / 4"
            }
            BoardSize.MEDIUM ->{
                moves.text="Medium : 6 x 3"
                pairs.text="Pairs : 0 / 9"
            }
            BoardSize.HARD->{
                moves.text="Hard : 6 x 4"
                pairs.text="Pairs : 0 / 12"
            }
        }
        pairs.setTextColor(ContextCompat.getColor(this,R.color.color_progress_none))
        memoryGame=MemoryGame(boardSize,customGameImages)
        adapter=MemoryBoardAdapter(this,boardSize,memoryGame.cards,object:MemoryBoardAdapter.CardClickListner{
            override fun onCardClicked(position: Int) {
                updateGameWithFlip(position)
            }
        })
        game_board.adapter=adapter
        game_board.setHasFixedSize(true)
        game_board.layoutManager= GridLayoutManager(this,boardSize.getWidth())
    }
    private fun updateGameWithFlip(position:Int){
        if(memoryGame.haveWonGame()){
            Snackbar.make(clRoot,"You Already Won!!!", Snackbar.LENGTH_LONG).show()
            return
        }
        if(memoryGame.isCardFaceUp(position)){
            Snackbar.make(clRoot,"Invalid!!!", Snackbar.LENGTH_SHORT).show()
            return
        }
        if(memoryGame.flipCart(position)){
            val color=android.animation.ArgbEvaluator().evaluate(
                    memoryGame.numPairsFound.toFloat()/boardSize.getNumPairs(),
                    ContextCompat.getColor(this,R.color.color_progress_none),
                    ContextCompat.getColor(this,R.color.color_progress_full)
            )as Int
            pairs.setTextColor(color)
            pairs.text="Pairs : ${memoryGame.numPairsFound} / ${boardSize.getNumPairs()}"
            if(memoryGame.haveWonGame()){
                Snackbar.make(clRoot,"You Won!!! Congratulations", Snackbar.LENGTH_LONG).show()
                CommonConfetti.rainingConfetti(clRoot, intArrayOf(Color.YELLOW,Color.GREEN,Color.MAGENTA)).oneShot()
            }
        }
        moves.text="Moves : ${memoryGame.getNumMoves()}"
        adapter.notifyDataSetChanged()
    }
}