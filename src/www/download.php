<?php

    include "./API.php";
    protectFile("download.php", "", __LINE__);
    
    if(!isset($_GET['f'])) {
        echo writeError(9098, loadString("download_no_get"));
        die();
    }
    
    if(!file_exists($API['root']."uploads/".$_GET['f'])) {
        echo writeError(9097, loadString("download_no_exist"));
        die();
    }
    
    $f = array(
        "filename" => $_GET['f'],
        "extention" => strtolower(substr(strrchr($_GET['f'],"."),1)),
        "basename" => basename($_GET['f']),
        "filesize" => filesize($_GET['f']),
        "mimetype" => mime_content_type($API['root']."uploads/".$_GET['f']),
        "full" => $API['root']."uploads/".$_GET['f']
    );
    
    header("Pragma: public");
    header("Expires: 0");
    header("Cache-Control: must-revalidate, post-check=0, pre-check=0");
    header("Cache-Control: private", false);
    header("Content-Type: " .$f['mimetype']. "");
    header("Content-Disposition: attachment; filename=\"" .$f['basename']. "\";" );
    header("Content-Transfer-Encoding: binary");
    header("Content-Length: " .$f['filesize']);
    readfile($f['full']);

?>