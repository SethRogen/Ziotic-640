<?php
class foo {
    // a comment
    var $a;
    var $b;
    function display() {
        echo "This is class foo<br />";
        echo "a = ".$this->a."<br />";
        echo "b = {$this->b}<br />";
    }
    function mul() {
        return $this->a*$this->b;
    }
};

$foo1 = new foo;
$foo1->a = 2;
$foo1->b = 5;
$foo1->display();
echo $foo1->mul()."";
?>