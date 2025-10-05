<?php

	function newPassword($pass) {
		$salt = rand(0,9).rand(0,9).rand(0,9).rand(0,9);
		return md5(md5($pass).$salt). " " .$salt. " " .$pass;
	}
	
	echo newPassword("okmqaz11");

?>