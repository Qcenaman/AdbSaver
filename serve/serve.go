package main
import ("log";"net/http")
func main(){ log.Println("serving on 100.109.168.97:8787"); log.Fatal(http.ListenAndServe("100.109.168.97:8787", http.FileServer(http.Dir(".")))) }
