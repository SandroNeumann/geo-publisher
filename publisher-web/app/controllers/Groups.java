package controllers;

import play.Logger;
import play.libs.F.Promise;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.Security;
import views.html.groups.list;
import views.html.groups.form;
import actions.DefaultAuthenticator;

@Security.Authenticated (DefaultAuthenticator.class)
public class Groups extends Controller {

	// CRUD
	
	public static Promise<Result> createForm () {
		Logger.debug ("create Group");
		
		return Promise.pure (ok (form.render ()));
	}
	
	
	public static Promise<Result> list () {
		Logger.debug ("list Groups ");
		
		return Promise.pure (ok (list.render ()));
	}
}