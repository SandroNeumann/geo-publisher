require ([
	'dojo/dom',
	'dojo/on',
	'dojo/_base/window',
	'dojo/query',
	'dojo/dom-attr',
	'dojo/dom-construct',
	'tree-select/tree-select',
	'put-selector/put',
	
	'dojo/NodeList-traverse',
	
	'dojo/domReady!'
], function (dom, on, win, query, domattr, domConstruct, TreeSelect, put) {
	var treeSelect = new TreeSelect ('.gp-tree-select', '.gp-tree-values');
	
	var keywordbutton = dom.byId('add-keyword');
	var keywordlist = dom.byId('keyword-list');
	
	on(keywordbutton,'click', function(evt) {
		var keywordinput = dom.byId('input-keywords').value;
		
		if(keywordinput !== "") {
			var el1 = put("div.keyword-item-block[value=$]", keywordinput);
			var el2 = put(el1, "div.keyword-item.label.label-primary", keywordinput);
			var el3 = put(el2, "button.close[type=button][aria-hidden=true][value=$]", keywordinput);
			el3.innerHTML = "&times;";
			
			put(keywordlist, el1);
		}
		
		dom.byId('input-keywords').value = "";
	});
	
	on(win.doc, ".close:click", function(event) {
		
		
		var valueItem = domattr.get(this, 'value');
		
		var itemToDel = query(this).parents(".keyword-item-block")[0];
		domConstruct.destroy(itemToDel);
	});
});

$(function () {
	$('[data-toggle="popover"]').popover();
});