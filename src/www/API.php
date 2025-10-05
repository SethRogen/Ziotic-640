<?php

	##############################
	#          JArD API          #
	##############################
		
	session_start();
		
	$API = array(
		"root" => "/Applications/XAMPP/xamppfiles/htdocs/JArD/"  
	);
	
	function loadConfig($name) {
		$file = $API['root']."xml/config.xml";
		$xml = simplexml_load_file($file);
		$count = count($xml->item);
		for($a = 0; $a < $count; $a++) {
			if($xml->item[$a]->name == $name) return $xml->item[$a]->string;
		}
		return "NULL";
	}
	
	function writeError($num, $str) {
		return "<html><head><title>Error {$num} - JArD</title></head><body><center><div style=\"color: #1057AE; width: 800px; font-size: 28px; font-family: Arial; text-align: left; padding: 9px 0px 0px 0px; margin-bottom: 20px; margin-top: 10px;\">JArD<span style=\"font-size: 12px; color: #5f8fce;\">&nbsp;&nbsp;- An unexpected error was encountered.</span></div><div style=\"border: 1px solid #cccccc; padding: 1px; width: 800px;\"><div style=\"background: #efefef; height: 23px; padding: 8px 0px 0px 8px; color: #777; font-family: Arial; font-size: 11px; text-align: left;\">Error {$num}</div><div style=\"padding: 7px; color: black; font-size: 10pt; font-family: arial; line-height: 15pt;\" onMouseOver=\"this.style.color='#1057AE'\" onMouseOut=\"this.style.color='black'\"><div style=\"height: 10px;\"></div>{$str}<br />If you think this is a server error, please contact the webmaster.<br /><br /><div style=\"text-align: right; color: #1057AE; font-size: 9px; font-weight: bold; line-height: 9px;\">Powered by JArD</div></div></div></center></body></html>";
	}
	
	function writeJARDMessage($title, $str) {
		return "<html><head><title>{$title} - JArD</title></head><body><center><div style=\"color: #1057AE; width: 800px; font-size: 28px; font-family: Arial; text-align: left; padding: 9px 0px 0px 0px; margin-bottom: 20px; margin-top: 10px;\">JArD<span style=\"font-size: 12px; color: #5f8fce;\"></span></div><div style=\"border: 1px solid #cccccc; padding: 1px; width: 800px;\"><div style=\"background: #efefef; height: 23px; padding: 8px 0px 0px 8px; color: #777; font-family: Arial; font-size: 11px; text-align: left;\">{$title}</div><div style=\"padding: 7px; color: black; font-size: 10pt; font-family: arial; line-height: 15pt;\" onMouseOver=\"this.style.color='#1057AE'\" onMouseOut=\"this.style.color='black'\"><div style=\"height: 10px;\"></div>{$str}<br /><br /><div style=\"text-align: right; color: #1057AE; font-size: 9px; font-weight: bold; line-height: 9px;\">Powered by JArD</div></div></div></center></body></html>";
	}
	
	if(loadConfig("DEBUG_MODE") == "false") {
		error_reporting(0);
		ini_set('display_errors', 0);
	}
	
	if(loadConfig("LOCKDOWN_MODE") == "true") {
		$error = parseString(loadString("api_lockdown"));
		echo writeError(9096, $error);
		die();
	}
	
	function newRandom($length) {
		$return = "";
		$char = array("a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m", "n", "o", "p", "q", "r", "s", "t", "u", "v", "w", "x", "y", "z", "!", "@", "#", "$", "%", "^", "&", "*", "(", ")", "[", "{", "]", "}");
		for($i = 0; $i < $length; $i++) {
			$type = rand(1,2);
			switch($type) {
				case 1:
					$r = rand(0,9);
					break;
				case 2:
					$r = $char[rand(0, count($char))];
					break;
			}
			$return .= $r;
		}
		return $return;
	}
	
	function strToChar($str, $char) {
		$return = "";
		if(strlen($char) > 1) {
			$char = substr($char, 0, 1);
		}
		for($i = 1; $i <= strlen($str); $i++) {
			$return .= $char;
		}
		return $return;
	}
	
	function loadString($name) {
		$file = $API['root']."xml/lang/" .loadConfig("LANG"). ".xml";
		if(file_exists($file)) {
			$xml = simplexml_load_file($file);
			$count = count($xml->item);
			for($a = 0; $a < $count; $a++) {
				if($xml->item[$a]->name == $name) {
					if($xml->item[$a]->disabled == "true") {
						return "The string [url=" .$name. "][b][" .$name. "][/b][/url] exists, but is disabled. If you are seeing this error, please notify an Administrator.";
					} else {
						return $xml->item[$a]->string;
					}
				}
			}
		}
		return "NULL";
	}
	
	function printStrings($lang = "en") {
		$file = $API['root']."xml/lang/" .$lang. ".xml";
		$xml = simplexml_load_file($file);
		$data = array("name", "string", "usesBB", "disabled");
		$count = array("xml" => count($xml->item), "data" => count($data));
		
		for($a = 0; $a < $count['xml']; $a++) {
			echo "<h3 style=\"margin-bottom: 0px;\">" .strtoupper($xml->item[$a]->name). "</h3>";
			echo "<b>id:</b> " .$a. "<br />";
			for($b = 0; $b < $count['data']; $b++) {
				if($xml->item[$a]->$data[$b]) echo "<b>" .$data[$b] . ":</b> " .$xml->item[$a]->$data[$b]. "<br />";
			}
			echo "<br />";
		}
	}
		
	function parseBBCode($string) {
		$bbcode = array(
			'#\[i\](.*?)\[/i\]#',
			'#\[b\](.*?)\[/b\]#',
			'#\[u\](.*?)\[/u\]#',
			'#\[img\](.*?)\[/img\]#',
			'#\[url=(.*?)\](.*?)\[/url\]#',
			'#\[code\](.*?)\[/code\]#',
			'#\[dotted\](.*?)\[/dotted\]#'
		);
		$html = array(
			'<i>\\1</i>',
			'<b>\\1</b>',
			'<u>\\1</u>',
			'<img src="\\1" alt="\\1" title="\\1" border="0" />',
			'<a href="\\1">\\2</a>',
			'<code>\\1</code>',
			'<span style="border: 1px dotted #ccc !important; padding: 3px;">\\1</span>'
		);
		return preg_replace($bbcode, $html, $string);
	}
		
	function parseSystemText($str) {
		$conf = array();
		$file = "./xml/config.xml";
		$xml = simplexml_load_file($file);
		$count = count($xml->item);
		for($a = 0; $a < $count; $a++) {
			// array_push($conf, $xml->item[$a]->string);
			if($xml->item[$a]->protect == "true") {
				$dastring = strToChar($xml->item[$a]->string, "*");
			} else {
				$dastring = $xml->item[$a]->string;
			}
			$str = str_replace("%/" .$xml->item[$a]->name. "/%", $dastring, $str);
		}
		return $str;
		// return preg_replace('#\%\/(.*?)\/%#', "" .loadConfig("". "\\1" .""). "", $str);
	}
	
	function parseString($str) {
		$str = str_replace(array("[br]", "[br /]"), array("<br>", "<br />"), $str);
		$str = parseBBCode($str);
		return $str;
	}
	
	function newPassword($pass) {
		$salt = newRandom(4);
		return array("" .md5(md5($pass).$salt). "", "" .$salt. "", "" .strToChar($pass, "*"). "");
	}
	
	function checkPassword($enc, $pass, $salt){
		if(md5(md5($pass).$salt) == $enc) {
			return true;
		} else {
			return false;
		}
	}
		
	function startConnection() {
		$connection = mysql_connect(loadConfig("MYSQL_HOST"), loadConfig("MYSQL_USER"), loadConfig("MYSQL_PASS"));
		if(!$connection) {
			echo parseSystemText(loadString("mysql_connection_error")). "<br />";
			return false;
			
		}
		return true;
	}
	
	function protectFile($file, $key = "", $line = null) {
		if(strpos($_SERVER['PHP_SELF'], $file)) {
			if(!empty($key)) {
				if(isset($_GET['accesskey'])) {
					if($_GET['accesskey']==$key) {
						return;
					}
				}
			}
			$error = parseString(loadString("direct_access_forbidden"). "[dotted]protectFile(\"{$file}\")[/dotted]");
			if($line) {
				$error .= parseString(" on line {$line}");
			}
			$error .= parseString(" in {$file}.");
			if(empty($key)) {
				$error .= parseString("<br />".loadString("file_no_accesskey"));
			}
			echo writeError(9099, $error);
			die();
		}
	}
	
	// Loads a configuration string from the config.xml file
	// echo loadConfig("FRAMEWORK")."<br />";
	
	// Loads a string from the default language xml file.
	// echo parseString(loadString("muted_warning"));
	
	// Creates a new random $length digit string.
	// echo newRandom(4);
	
	// Prints out all the strings from the default language xml file.
	// echo printStrings();
	
	// Creates a new password.
	// $pass = newPassword("4eTudu--ac4");
	
	// Reads the password and sorts them into the Encrypted/Salt/Original.
	// echo "Encrypted: ".$pass[0]."<br />Salt: ".$pass[1]. "<br />Original: ".$pass[2];
	
	// Starts the mysql link.	
	// if(!startConnection()) {
	// 	die();
	// }
	
	// Converts a string to a string that is replaced with a given character.
	// echo strToChar("123", "*");
	
	// PHP Info
	// phpinfo();
	
	protectFile("API.php", "", __LINE__);
	
?>