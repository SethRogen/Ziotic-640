<?php

    include "../API.php";
    
    if(file_exists($API['root']."xml/config.xml")) {
        writeJARDMessage("Welcome to JArD", "This pages shows you have sucessfully installed JArD onto your personal webserver.");
    } else {
        writeError(9095, parseString(loadString("new_install_welcome")));
    }

?>