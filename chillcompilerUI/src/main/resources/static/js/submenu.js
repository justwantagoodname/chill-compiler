// 定义各个菜单的数据
const menus = {
    'menu-add': ['新建文件', '新建文件夹', '导入项目'],
    'menu-file': ['我的文档', '最近打开', '下载文件'],
    'menu-tools': ['设置', '插件管理', '主题切换']
};

function toggleMenu(menuKey) {
    const submenu = document.getElementById('submenu');
    const submenuContent = document.getElementById('submenu-content');

    submenu.classList.remove('collapsed'); // 展开子菜单

    submenuContent.innerHTML = '';

    const ul = document.createElement('ul');
    menus[menuKey].forEach(item => {
        const li = document.createElement('li');
        li.textContent = item;
        ul.appendChild(li);
    });

    submenuContent.appendChild(ul);
}

function collapseMenu() {
    const submenu = document.getElementById('submenu');
    submenu.classList.add('collapsed');
}