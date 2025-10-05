<?php

    // Include the API system
    include "./API.php";
    protectFile("password.php", "", __LINE__);
    
    if(isset($_POST['pass'])) {
        $pass = newPassword($_POST['pass']);
        echo    "Encrypted: " .$pass[0].
                "<br />".
                "Salt: " .$pass[1].
                "<br />".
                "Original: " .$pass[2];
    } else {
        echo "<form action=\"\" method=\"post\">Password: <input name=\"pass\" type=\"password\"><input type=\"submit\" value=\"Submit!\"></form>";
    }

?>