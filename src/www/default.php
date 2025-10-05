<?php

    include "./API.php";
    
    if(file_exists("xml/config.xml")) {
        echo writeJARDMessage("Welcome to JArD", "This pages shows you have sucessfully installed JArD onto your webserver.");
    } else {
        echo writeError(9095, parseString(loadString("new_install_welcome")));
    }

?>