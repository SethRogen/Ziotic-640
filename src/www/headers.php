<?php
   /* include "./API.php";
   foreach($_SERVER as $h=>$v)
     if(ereg('HTTP_(.+)',$h,$hp))
       echo "<li>$h = $v</li>\n";
   header('Content-type: text/html'); */
   header("Content-type: application/pdf");
   readfile("Homework Book.pdf");
?>