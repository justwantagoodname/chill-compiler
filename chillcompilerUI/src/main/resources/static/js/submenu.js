function toggleMenu() {
    const submenu = document.getElementById('add-submenu');
    if(submenu.classList.contains('collapsed')) {
        submenu.classList.remove('collapsed'); // 展开子菜单
    } else {
        submenu.classList.add('collapsed');
    }
}

function addWindows(type){

}